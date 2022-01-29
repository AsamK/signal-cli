/*
  Copyright (C) 2015-2022 AsamK and contributors

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.asamk.signal.manager;

import org.asamk.signal.manager.api.Configuration;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.manager.api.InactiveGroupLinkException;
import org.asamk.signal.manager.api.InvalidDeviceLinkException;
import org.asamk.signal.manager.api.InvalidStickerException;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResult;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.StickerPack;
import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.manager.api.StickerPackUrl;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.api.UpdateGroup;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.stickers.Sticker;
import org.asamk.signal.manager.util.AttachmentUtils;
import org.asamk.signal.manager.util.KeyUtils;
import org.asamk.signal.manager.util.StickerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class ManagerImpl implements Manager {

    private final static Logger logger = LoggerFactory.getLogger(ManagerImpl.class);

    private SignalAccount account;
    private final SignalDependencies dependencies;
    private final Context context;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private Thread receiveThread;
    private boolean isReceivingSynchronous;
    private final Set<ReceiveMessageHandler> weakHandlers = new HashSet<>();
    private final Set<ReceiveMessageHandler> messageHandlers = new HashSet<>();
    private final List<Runnable> closedListeners = new ArrayList<>();
    private final CompositeDisposable disposable = new CompositeDisposable();

    ManagerImpl(
            SignalAccount account,
            PathConfig pathConfig,
            ServiceEnvironmentConfig serviceEnvironmentConfig,
            String userAgent
    ) {
        this.account = account;

        final var credentialsProvider = new DynamicCredentialsProvider(account.getAci(),
                account.getAccount(),
                account.getPassword(),
                account.getDeviceId());
        final var sessionLock = new SignalSessionLock() {
            private final ReentrantLock LEGACY_LOCK = new ReentrantLock();

            @Override
            public Lock acquire() {
                LEGACY_LOCK.lock();
                return LEGACY_LOCK::unlock;
            }
        };
        this.dependencies = new SignalDependencies(serviceEnvironmentConfig,
                userAgent,
                credentialsProvider,
                account.getSignalProtocolStore(),
                executor,
                sessionLock);
        final var avatarStore = new AvatarStore(pathConfig.avatarsPath());
        final var attachmentStore = new AttachmentStore(pathConfig.attachmentsPath());
        final var stickerPackStore = new StickerPackStore(pathConfig.stickerPacksPath());

        this.context = new Context(account, dependencies, avatarStore, attachmentStore, stickerPackStore);
        this.context.getAccountHelper().setUnregisteredListener(this::close);
        this.context.getReceiveHelper().setAuthenticationFailureListener(this::close);
        this.context.getReceiveHelper().setCaughtUpWithOldMessagesListener(() -> {
            synchronized (this) {
                this.notifyAll();
            }
        });
        disposable.add(account.getIdentityKeyStore().getIdentityChanges().subscribe(recipientId -> {
            logger.trace("Archiving old sessions for {}", recipientId);
            account.getSessionStore().archiveSessions(recipientId);
            account.getSenderKeyStore().deleteSharedWith(recipientId);
            final var profile = account.getRecipientStore().getProfile(recipientId);
            if (profile != null) {
                account.getRecipientStore()
                        .storeProfile(recipientId,
                                Profile.newBuilder(profile)
                                        .withUnidentifiedAccessMode(Profile.UnidentifiedAccessMode.UNKNOWN)
                                        .withLastUpdateTimestamp(0)
                                        .build());
            }
        }));
    }

    @Override
    public String getSelfNumber() {
        return account.getAccount();
    }

    @Override
    public void checkAccountState() throws IOException {
        context.getAccountHelper().checkAccountState();
    }

    @Override
    public Map<String, Pair<String, UUID>> areUsersRegistered(Set<String> numbers) throws IOException {
        final var canonicalizedNumbers = numbers.stream().collect(Collectors.toMap(n -> n, n -> {
            try {
                final var canonicalizedNumber = PhoneNumberFormatter.formatNumber(n, account.getAccount());
                if (!canonicalizedNumber.equals(n)) {
                    logger.debug("Normalized number {} to {}.", n, canonicalizedNumber);
                }
                return canonicalizedNumber;
            } catch (InvalidNumberException e) {
                return "";
            }
        }));

        // Note "registeredUsers" has no optionals. It only gives us info on users who are registered
        final var canonicalizedNumbersSet = canonicalizedNumbers.values()
                .stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        final var registeredUsers = context.getRecipientHelper().getRegisteredUsers(canonicalizedNumbersSet);

        return numbers.stream().collect(Collectors.toMap(n -> n, n -> {
            final var number = canonicalizedNumbers.get(n);
            final var aci = registeredUsers.get(number);
            return new Pair<>(number.isEmpty() ? null : number, aci == null ? null : aci.uuid());
        }));
    }

    @Override
    public void updateAccountAttributes(String deviceName) throws IOException {
        if (deviceName != null) {
            context.getAccountHelper().setDeviceName(deviceName);
        }
        context.getAccountHelper().updateAccountAttributes();
    }

    @Override
    public Configuration getConfiguration() {
        final var configurationStore = account.getConfigurationStore();
        return Configuration.from(configurationStore);
    }

    @Override
    public void updateConfiguration(
            Configuration configuration
    ) throws NotMasterDeviceException {
        if (!account.isMasterDevice()) {
            throw new NotMasterDeviceException();
        }

        final var configurationStore = account.getConfigurationStore();
        if (configuration.readReceipts().isPresent()) {
            configurationStore.setReadReceipts(configuration.readReceipts().get());
        }
        if (configuration.unidentifiedDeliveryIndicators().isPresent()) {
            configurationStore.setUnidentifiedDeliveryIndicators(configuration.unidentifiedDeliveryIndicators().get());
        }
        if (configuration.typingIndicators().isPresent()) {
            configurationStore.setTypingIndicators(configuration.typingIndicators().get());
        }
        if (configuration.linkPreviews().isPresent()) {
            configurationStore.setLinkPreviews(configuration.linkPreviews().get());
        }
        context.getSyncHelper().sendConfigurationMessage();
    }

    @Override
    public void setProfile(
            String givenName, final String familyName, String about, String aboutEmoji, java.util.Optional<File> avatar
    ) throws IOException {
        context.getProfileHelper()
                .setProfile(givenName,
                        familyName,
                        about,
                        aboutEmoji,
                        avatar == null ? null : Optional.fromNullable(avatar.orElse(null)));
        context.getSyncHelper().sendSyncFetchProfileMessage();
    }

    @Override
    public void unregister() throws IOException {
        context.getAccountHelper().unregister();
    }

    @Override
    public void deleteAccount() throws IOException {
        context.getAccountHelper().deleteAccount();
    }

    @Override
    public void submitRateLimitRecaptchaChallenge(String challenge, String captcha) throws IOException {
        captcha = captcha == null ? null : captcha.replace("signalcaptcha://", "");

        dependencies.getAccountManager().submitRateLimitRecaptchaChallenge(challenge, captcha);
    }

    @Override
    public List<Device> getLinkedDevices() throws IOException {
        var devices = dependencies.getAccountManager().getDevices();
        account.setMultiDevice(devices.size() > 1);
        var identityKey = account.getIdentityKeyPair().getPrivateKey();
        return devices.stream().map(d -> {
            String deviceName = d.getName();
            if (deviceName != null) {
                try {
                    deviceName = DeviceNameUtil.decryptDeviceName(deviceName, identityKey);
                } catch (IOException e) {
                    logger.debug("Failed to decrypt device name, maybe plain text?", e);
                }
            }
            return new Device(d.getId(),
                    deviceName,
                    d.getCreated(),
                    d.getLastSeen(),
                    d.getId() == account.getDeviceId());
        }).toList();
    }

    @Override
    public void removeLinkedDevices(int deviceId) throws IOException {
        context.getAccountHelper().removeLinkedDevices(deviceId);
    }

    @Override
    public void addDeviceLink(URI linkUri) throws IOException, InvalidDeviceLinkException {
        var deviceLinkInfo = DeviceLinkInfo.parseDeviceLinkUri(linkUri);
        context.getAccountHelper().addDevice(deviceLinkInfo);
    }

    @Override
    public void setRegistrationLockPin(java.util.Optional<String> pin) throws IOException, NotMasterDeviceException {
        if (!account.isMasterDevice()) {
            throw new NotMasterDeviceException();
        }
        if (pin.isPresent()) {
            context.getAccountHelper().setRegistrationPin(pin.get());
        } else {
            context.getAccountHelper().removeRegistrationPin();
        }
    }

    void refreshPreKeys() throws IOException {
        context.getPreKeyHelper().refreshPreKeys();
    }

    @Override
    public Profile getRecipientProfile(RecipientIdentifier.Single recipient) throws IOException, UnregisteredRecipientException {
        return context.getProfileHelper().getRecipientProfile(context.getRecipientHelper().resolveRecipient(recipient));
    }

    @Override
    public List<Group> getGroups() {
        return account.getGroupStore().getGroups().stream().map(this::toGroup).toList();
    }

    private Group toGroup(final GroupInfo groupInfo) {
        if (groupInfo == null) {
            return null;
        }

        return Group.from(groupInfo,
                account.getRecipientStore()::resolveRecipientAddress,
                account.getSelfRecipientId());
    }

    @Override
    public SendGroupMessageResults quitGroup(
            GroupId groupId, Set<RecipientIdentifier.Single> groupAdmins
    ) throws GroupNotFoundException, IOException, NotAGroupMemberException, LastGroupAdminException, UnregisteredRecipientException {
        final var newAdmins = context.getRecipientHelper().resolveRecipients(groupAdmins);
        return context.getGroupHelper().quitGroup(groupId, newAdmins);
    }

    @Override
    public void deleteGroup(GroupId groupId) throws IOException {
        context.getGroupHelper().deleteGroup(groupId);
    }

    @Override
    public Pair<GroupId, SendGroupMessageResults> createGroup(
            String name, Set<RecipientIdentifier.Single> members, File avatarFile
    ) throws IOException, AttachmentInvalidException, UnregisteredRecipientException {
        return context.getGroupHelper()
                .createGroup(name,
                        members == null ? null : context.getRecipientHelper().resolveRecipients(members),
                        avatarFile);
    }

    @Override
    public SendGroupMessageResults updateGroup(
            final GroupId groupId, final UpdateGroup updateGroup
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, GroupSendingNotAllowedException, UnregisteredRecipientException {
        return context.getGroupHelper()
                .updateGroup(groupId,
                        updateGroup.getName(),
                        updateGroup.getDescription(),
                        updateGroup.getMembers() == null
                                ? null
                                : context.getRecipientHelper().resolveRecipients(updateGroup.getMembers()),
                        updateGroup.getRemoveMembers() == null
                                ? null
                                : context.getRecipientHelper().resolveRecipients(updateGroup.getRemoveMembers()),
                        updateGroup.getAdmins() == null
                                ? null
                                : context.getRecipientHelper().resolveRecipients(updateGroup.getAdmins()),
                        updateGroup.getRemoveAdmins() == null
                                ? null
                                : context.getRecipientHelper().resolveRecipients(updateGroup.getRemoveAdmins()),
                        updateGroup.isResetGroupLink(),
                        updateGroup.getGroupLinkState(),
                        updateGroup.getAddMemberPermission(),
                        updateGroup.getEditDetailsPermission(),
                        updateGroup.getAvatarFile(),
                        updateGroup.getExpirationTimer(),
                        updateGroup.getIsAnnouncementGroup());
    }

    @Override
    public Pair<GroupId, SendGroupMessageResults> joinGroup(
            GroupInviteLinkUrl inviteLinkUrl
    ) throws IOException, InactiveGroupLinkException {
        return context.getGroupHelper().joinGroup(inviteLinkUrl);
    }

    private SendMessageResults sendMessage(
            SignalServiceDataMessage.Builder messageBuilder, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        var results = new HashMap<RecipientIdentifier, List<SendMessageResult>>();
        long timestamp = System.currentTimeMillis();
        messageBuilder.withTimestamp(timestamp);
        for (final var recipient : recipients) {
            if (recipient instanceof RecipientIdentifier.Single single) {
                try {
                    final var recipientId = context.getRecipientHelper().resolveRecipient(single);
                    final var result = context.getSendHelper().sendMessage(messageBuilder, recipientId);
                    results.put(recipient, List.of(toSendMessageResult(result)));
                } catch (UnregisteredRecipientException e) {
                    results.put(recipient,
                            List.of(SendMessageResult.unregisteredFailure(single.toPartialRecipientAddress())));
                }
            } else if (recipient instanceof RecipientIdentifier.NoteToSelf) {
                final var result = context.getSendHelper().sendSelfMessage(messageBuilder);
                results.put(recipient, List.of(toSendMessageResult(result)));
            } else if (recipient instanceof RecipientIdentifier.Group group) {
                final var result = context.getSendHelper().sendAsGroupMessage(messageBuilder, group.groupId());
                results.put(recipient, result.stream().map(this::toSendMessageResult).toList());
            }
        }
        return new SendMessageResults(timestamp, results);
    }

    private SendMessageResult toSendMessageResult(final org.whispersystems.signalservice.api.messages.SendMessageResult result) {
        return SendMessageResult.from(result,
                account.getRecipientStore(),
                account.getRecipientStore()::resolveRecipientAddress);
    }

    private SendMessageResults sendTypingMessage(
            SignalServiceTypingMessage.Action action, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        var results = new HashMap<RecipientIdentifier, List<SendMessageResult>>();
        final var timestamp = System.currentTimeMillis();
        for (var recipient : recipients) {
            if (recipient instanceof RecipientIdentifier.Single single) {
                final var message = new SignalServiceTypingMessage(action, timestamp, Optional.absent());
                try {
                    final var recipientId = context.getRecipientHelper().resolveRecipient(single);
                    final var result = context.getSendHelper().sendTypingMessage(message, recipientId);
                    results.put(recipient, List.of(toSendMessageResult(result)));
                } catch (UnregisteredRecipientException e) {
                    results.put(recipient,
                            List.of(SendMessageResult.unregisteredFailure(single.toPartialRecipientAddress())));
                }
            } else if (recipient instanceof RecipientIdentifier.Group) {
                final var groupId = ((RecipientIdentifier.Group) recipient).groupId();
                final var message = new SignalServiceTypingMessage(action, timestamp, Optional.of(groupId.serialize()));
                final var result = context.getSendHelper().sendGroupTypingMessage(message, groupId);
                results.put(recipient, result.stream().map(this::toSendMessageResult).toList());
            }
        }
        return new SendMessageResults(timestamp, results);
    }

    @Override
    public SendMessageResults sendTypingMessage(
            TypingAction action, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        return sendTypingMessage(action.toSignalService(), recipients);
    }

    @Override
    public SendMessageResults sendReadReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    ) throws IOException {
        final var timestamp = System.currentTimeMillis();
        var receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ,
                messageIds,
                timestamp);

        return sendReceiptMessage(sender, timestamp, receiptMessage);
    }

    @Override
    public SendMessageResults sendViewedReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    ) throws IOException {
        final var timestamp = System.currentTimeMillis();
        var receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.VIEWED,
                messageIds,
                timestamp);

        return sendReceiptMessage(sender, timestamp, receiptMessage);
    }

    private SendMessageResults sendReceiptMessage(
            final RecipientIdentifier.Single sender,
            final long timestamp,
            final SignalServiceReceiptMessage receiptMessage
    ) throws IOException {
        try {
            final var result = context.getSendHelper()
                    .sendReceiptMessage(receiptMessage, context.getRecipientHelper().resolveRecipient(sender));
            return new SendMessageResults(timestamp, Map.of(sender, List.of(toSendMessageResult(result))));
        } catch (UnregisteredRecipientException e) {
            return new SendMessageResults(timestamp,
                    Map.of(sender, List.of(SendMessageResult.unregisteredFailure(sender.toPartialRecipientAddress()))));
        }
    }

    @Override
    public SendMessageResults sendMessage(
            Message message, Set<RecipientIdentifier> recipients
    ) throws IOException, AttachmentInvalidException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException, UnregisteredRecipientException, InvalidStickerException {
        final var messageBuilder = SignalServiceDataMessage.newBuilder();
        applyMessage(messageBuilder, message);
        return sendMessage(messageBuilder, recipients);
    }

    private void applyMessage(
            final SignalServiceDataMessage.Builder messageBuilder, final Message message
    ) throws AttachmentInvalidException, IOException, UnregisteredRecipientException, InvalidStickerException {
        messageBuilder.withBody(message.messageText());
        final var attachments = message.attachments();
        if (attachments != null) {
            messageBuilder.withAttachments(context.getAttachmentHelper().uploadAttachments(attachments));
        }
        if (message.mentions().size() > 0) {
            messageBuilder.withMentions(resolveMentions(message.mentions()));
        }
        if (message.quote().isPresent()) {
            final var quote = message.quote().get();
            messageBuilder.withQuote(new SignalServiceDataMessage.Quote(quote.timestamp(),
                    context.getRecipientHelper()
                            .resolveSignalServiceAddress(context.getRecipientHelper().resolveRecipient(quote.author())),
                    quote.message(),
                    List.of(),
                    resolveMentions(quote.mentions())));
        }
        if (message.sticker().isPresent()) {
            final var sticker = message.sticker().get();
            final var packId = StickerPackId.deserialize(sticker.packId());
            final var stickerId = sticker.stickerId();

            final var stickerPack = context.getAccount().getStickerStore().getStickerPack(packId);
            if (stickerPack == null) {
                throw new InvalidStickerException("Sticker pack not found");
            }
            final var manifest = context.getStickerHelper().getOrRetrieveStickerPack(packId, stickerPack.getPackKey());
            if (manifest.stickers().size() <= stickerId) {
                throw new InvalidStickerException("Sticker id not part of this pack");
            }
            final var manifestSticker = manifest.stickers().get(stickerId);
            final var streamDetails = context.getStickerPackStore().retrieveSticker(packId, stickerId);
            if (streamDetails == null) {
                throw new InvalidStickerException("Missing local sticker file");
            }
            messageBuilder.withSticker(new SignalServiceDataMessage.Sticker(packId.serialize(),
                    stickerPack.getPackKey(),
                    stickerId,
                    manifestSticker.emoji(),
                    AttachmentUtils.createAttachment(streamDetails, Optional.absent())));
        }
    }

    private ArrayList<SignalServiceDataMessage.Mention> resolveMentions(final List<Message.Mention> mentionList) throws IOException, UnregisteredRecipientException {
        final var mentions = new ArrayList<SignalServiceDataMessage.Mention>();
        for (final var m : mentionList) {
            final var recipientId = context.getRecipientHelper().resolveRecipient(m.recipient());
            mentions.add(new SignalServiceDataMessage.Mention(context.getRecipientHelper()
                    .resolveSignalServiceAddress(recipientId)
                    .getAci(), m.start(), m.length()));
        }
        return mentions;
    }

    @Override
    public SendMessageResults sendRemoteDeleteMessage(
            long targetSentTimestamp, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        var delete = new SignalServiceDataMessage.RemoteDelete(targetSentTimestamp);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withRemoteDelete(delete);
        for (final var recipient : recipients) {
            if (recipient instanceof RecipientIdentifier.Single r) {
                try {
                    final var recipientId = context.getRecipientHelper().resolveRecipient(r);
                    account.getMessageSendLogStore().deleteEntryForRecipientNonGroup(targetSentTimestamp, recipientId);
                } catch (UnregisteredRecipientException ignored) {
                }
            } else if (recipient instanceof RecipientIdentifier.Group r) {
                account.getMessageSendLogStore().deleteEntryForGroup(targetSentTimestamp, r.groupId());
            }
        }
        return sendMessage(messageBuilder, recipients);
    }

    @Override
    public SendMessageResults sendMessageReaction(
            String emoji,
            boolean remove,
            RecipientIdentifier.Single targetAuthor,
            long targetSentTimestamp,
            Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException, UnregisteredRecipientException {
        var targetAuthorRecipientId = context.getRecipientHelper().resolveRecipient(targetAuthor);
        var reaction = new SignalServiceDataMessage.Reaction(emoji,
                remove,
                context.getRecipientHelper().resolveSignalServiceAddress(targetAuthorRecipientId),
                targetSentTimestamp);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withReaction(reaction);
        return sendMessage(messageBuilder, recipients);
    }

    @Override
    public SendMessageResults sendEndSessionMessage(Set<RecipientIdentifier.Single> recipients) throws IOException {
        var messageBuilder = SignalServiceDataMessage.newBuilder().asEndSessionMessage();

        try {
            return sendMessage(messageBuilder,
                    recipients.stream().map(RecipientIdentifier.class::cast).collect(Collectors.toSet()));
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new AssertionError(e);
        } finally {
            for (var recipient : recipients) {
                final RecipientId recipientId;
                try {
                    recipientId = context.getRecipientHelper().resolveRecipient(recipient);
                } catch (UnregisteredRecipientException e) {
                    continue;
                }
                account.getSessionStore().deleteAllSessions(recipientId);
            }
        }
    }

    @Override
    public void deleteRecipient(final RecipientIdentifier.Single recipient) {
        account.removeRecipient(account.getRecipientStore().resolveRecipient(recipient.toPartialRecipientAddress()));
    }

    @Override
    public void deleteContact(final RecipientIdentifier.Single recipient) {
        account.getContactStore()
                .deleteContact(account.getRecipientStore().resolveRecipient(recipient.toPartialRecipientAddress()));
    }

    @Override
    public void setContactName(
            RecipientIdentifier.Single recipient, String name
    ) throws NotMasterDeviceException, IOException, UnregisteredRecipientException {
        if (!account.isMasterDevice()) {
            throw new NotMasterDeviceException();
        }
        context.getContactHelper().setContactName(context.getRecipientHelper().resolveRecipient(recipient), name);
    }

    @Override
    public void setContactBlocked(
            RecipientIdentifier.Single recipient, boolean blocked
    ) throws NotMasterDeviceException, IOException, UnregisteredRecipientException {
        if (!account.isMasterDevice()) {
            throw new NotMasterDeviceException();
        }
        context.getContactHelper().setContactBlocked(context.getRecipientHelper().resolveRecipient(recipient), blocked);
        // TODO cycle our profile key, if we're not together in a group with recipient
        context.getSyncHelper().sendBlockedList();
    }

    @Override
    public void setGroupBlocked(
            final GroupId groupId, final boolean blocked
    ) throws GroupNotFoundException, NotMasterDeviceException {
        if (!account.isMasterDevice()) {
            throw new NotMasterDeviceException();
        }
        context.getGroupHelper().setGroupBlocked(groupId, blocked);
        // TODO cycle our profile key
        context.getSyncHelper().sendBlockedList();
    }

    @Override
    public void setExpirationTimer(
            RecipientIdentifier.Single recipient, int messageExpirationTimer
    ) throws IOException, UnregisteredRecipientException {
        var recipientId = context.getRecipientHelper().resolveRecipient(recipient);
        context.getContactHelper().setExpirationTimer(recipientId, messageExpirationTimer);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().asExpirationUpdate();
        try {
            sendMessage(messageBuilder, Set.of(recipient));
        } catch (NotAGroupMemberException | GroupNotFoundException | GroupSendingNotAllowedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public StickerPackUrl uploadStickerPack(File path) throws IOException, StickerPackInvalidException {
        var manifest = StickerUtils.getSignalServiceStickerManifestUpload(path);

        var messageSender = dependencies.getMessageSender();

        var packKey = KeyUtils.createStickerUploadKey();
        var packIdString = messageSender.uploadStickerManifest(manifest, packKey);
        var packId = StickerPackId.deserialize(Hex.fromStringCondensed(packIdString));

        var sticker = new Sticker(packId, packKey);
        account.getStickerStore().updateSticker(sticker);

        return new StickerPackUrl(packId, packKey);
    }

    @Override
    public List<StickerPack> getStickerPacks() {
        final var stickerPackStore = context.getStickerPackStore();
        return account.getStickerStore().getStickerPacks().stream().map(pack -> {
            if (stickerPackStore.existsStickerPack(pack.getPackId())) {
                try {
                    final var manifest = stickerPackStore.retrieveManifest(pack.getPackId());
                    return new StickerPack(pack.getPackId(),
                            new StickerPackUrl(pack.getPackId(), pack.getPackKey()),
                            pack.isInstalled(),
                            manifest.title(),
                            manifest.author(),
                            java.util.Optional.ofNullable(manifest.cover() == null ? null : manifest.cover().toApi()),
                            manifest.stickers().stream().map(JsonStickerPack.JsonSticker::toApi).toList());
                } catch (Exception e) {
                    logger.warn("Failed to read local sticker pack manifest: {}", e.getMessage(), e);
                }
            }

            return new StickerPack(pack.getPackId(), pack.getPackKey(), pack.isInstalled());
        }).toList();
    }

    @Override
    public void requestAllSyncData() throws IOException {
        context.getSyncHelper().requestAllSyncData();
        retrieveRemoteStorage();
    }

    void retrieveRemoteStorage() throws IOException {
        if (account.getStorageKey() != null) {
            context.getStorageHelper().readDataFromStorage();
        }
    }

    @Override
    public void addReceiveHandler(final ReceiveMessageHandler handler, final boolean isWeakListener) {
        if (isReceivingSynchronous) {
            throw new IllegalStateException("Already receiving message synchronously.");
        }
        synchronized (messageHandlers) {
            if (isWeakListener) {
                weakHandlers.add(handler);
            } else {
                messageHandlers.add(handler);
                startReceiveThreadIfRequired();
            }
        }
    }

    private static final AtomicInteger threadNumber = new AtomicInteger(0);

    private void startReceiveThreadIfRequired() {
        if (receiveThread != null) {
            return;
        }
        receiveThread = new Thread(() -> {
            logger.debug("Starting receiving messages");
            while (!Thread.interrupted()) {
                try {
                    context.getReceiveHelper().receiveMessages(Duration.ofMinutes(1), false, (envelope, e) -> {
                        synchronized (messageHandlers) {
                            Stream.concat(messageHandlers.stream(), weakHandlers.stream()).forEach(h -> {
                                try {
                                    h.handleMessage(envelope, e);
                                } catch (Exception ex) {
                                    logger.warn("Message handler failed, ignoring", ex);
                                }
                            });
                        }
                    });
                    break;
                } catch (IOException e) {
                    logger.warn("Receiving messages failed, retrying", e);
                }
            }
            logger.debug("Finished receiving messages");
            synchronized (messageHandlers) {
                receiveThread = null;

                // Check if in the meantime another handler has been registered
                if (!messageHandlers.isEmpty()) {
                    logger.debug("Another handler has been registered, starting receive thread again");
                    startReceiveThreadIfRequired();
                }
            }
        });
        receiveThread.setName("receive-" + threadNumber.getAndIncrement());

        receiveThread.start();
    }

    @Override
    public void removeReceiveHandler(final ReceiveMessageHandler handler) {
        final Thread thread;
        synchronized (messageHandlers) {
            weakHandlers.remove(handler);
            messageHandlers.remove(handler);
            if (!messageHandlers.isEmpty() || receiveThread == null || isReceivingSynchronous) {
                return;
            }
            thread = receiveThread;
            receiveThread = null;
        }

        stopReceiveThread(thread);
    }

    private void stopReceiveThread(final Thread thread) {
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public boolean isReceiving() {
        if (isReceivingSynchronous) {
            return true;
        }
        synchronized (messageHandlers) {
            return messageHandlers.size() > 0;
        }
    }

    @Override
    public void receiveMessages(Duration timeout, ReceiveMessageHandler handler) throws IOException {
        receiveMessages(timeout, true, handler);
    }

    @Override
    public void receiveMessages(ReceiveMessageHandler handler) throws IOException {
        receiveMessages(Duration.ofMinutes(1), false, handler);
    }

    private void receiveMessages(
            Duration timeout, boolean returnOnTimeout, ReceiveMessageHandler handler
    ) throws IOException {
        if (isReceiving()) {
            throw new IllegalStateException("Already receiving message.");
        }
        isReceivingSynchronous = true;
        receiveThread = Thread.currentThread();
        try {
            context.getReceiveHelper().receiveMessages(timeout, returnOnTimeout, handler);
        } finally {
            receiveThread = null;
            isReceivingSynchronous = false;
        }
    }

    @Override
    public void setIgnoreAttachments(final boolean ignoreAttachments) {
        context.getReceiveHelper().setIgnoreAttachments(ignoreAttachments);
    }

    @Override
    public boolean hasCaughtUpWithOldMessages() {
        return context.getReceiveHelper().hasCaughtUpWithOldMessages();
    }

    @Override
    public boolean isContactBlocked(final RecipientIdentifier.Single recipient) {
        final RecipientId recipientId;
        try {
            recipientId = context.getRecipientHelper().resolveRecipient(recipient);
        } catch (IOException | UnregisteredRecipientException e) {
            return false;
        }
        return context.getContactHelper().isContactBlocked(recipientId);
    }

    @Override
    public void sendContacts() throws IOException {
        context.getSyncHelper().sendContacts();
    }

    @Override
    public List<Pair<RecipientAddress, Contact>> getContacts() {
        return account.getContactStore()
                .getContacts()
                .stream()
                .map(p -> new Pair<>(account.getRecipientStore().resolveRecipientAddress(p.first()), p.second()))
                .toList();
    }

    @Override
    public String getContactOrProfileName(RecipientIdentifier.Single recipient) {
        final RecipientId recipientId;
        try {
            recipientId = context.getRecipientHelper().resolveRecipient(recipient);
        } catch (IOException | UnregisteredRecipientException e) {
            return null;
        }

        final var contact = account.getContactStore().getContact(recipientId);
        if (contact != null && !Util.isEmpty(contact.getName())) {
            return contact.getName();
        }

        final var profile = context.getProfileHelper().getRecipientProfile(recipientId);
        if (profile != null) {
            return profile.getDisplayName();
        }

        return null;
    }

    @Override
    public Group getGroup(GroupId groupId) {
        return toGroup(context.getGroupHelper().getGroup(groupId));
    }

    @Override
    public List<Identity> getIdentities() {
        return account.getIdentityKeyStore().getIdentities().stream().map(this::toIdentity).toList();
    }

    private Identity toIdentity(final IdentityInfo identityInfo) {
        if (identityInfo == null) {
            return null;
        }

        final var address = account.getRecipientStore().resolveRecipientAddress(identityInfo.getRecipientId());
        final var scannableFingerprint = context.getIdentityHelper()
                .computeSafetyNumberForScanning(identityInfo.getRecipientId(), identityInfo.getIdentityKey());
        return new Identity(address,
                identityInfo.getIdentityKey(),
                context.getIdentityHelper()
                        .computeSafetyNumber(identityInfo.getRecipientId(), identityInfo.getIdentityKey()),
                scannableFingerprint == null ? null : scannableFingerprint.getSerialized(),
                identityInfo.getTrustLevel(),
                identityInfo.getDateAdded());
    }

    @Override
    public List<Identity> getIdentities(RecipientIdentifier.Single recipient) {
        IdentityInfo identity;
        try {
            identity = account.getIdentityKeyStore()
                    .getIdentity(context.getRecipientHelper().resolveRecipient(recipient));
        } catch (IOException | UnregisteredRecipientException e) {
            identity = null;
        }
        return identity == null ? List.of() : List.of(toIdentity(identity));
    }

    @Override
    public boolean trustIdentityVerified(
            RecipientIdentifier.Single recipient, byte[] fingerprint
    ) throws UnregisteredRecipientException {
        return trustIdentity(recipient, r -> context.getIdentityHelper().trustIdentityVerified(r, fingerprint));
    }

    @Override
    public boolean trustIdentityVerifiedSafetyNumber(
            RecipientIdentifier.Single recipient, String safetyNumber
    ) throws UnregisteredRecipientException {
        return trustIdentity(recipient,
                r -> context.getIdentityHelper().trustIdentityVerifiedSafetyNumber(r, safetyNumber));
    }

    @Override
    public boolean trustIdentityVerifiedSafetyNumber(
            RecipientIdentifier.Single recipient, byte[] safetyNumber
    ) throws UnregisteredRecipientException {
        return trustIdentity(recipient,
                r -> context.getIdentityHelper().trustIdentityVerifiedSafetyNumber(r, safetyNumber));
    }

    @Override
    public boolean trustIdentityAllKeys(RecipientIdentifier.Single recipient) throws UnregisteredRecipientException {
        return trustIdentity(recipient, r -> context.getIdentityHelper().trustIdentityAllKeys(r));
    }

    private boolean trustIdentity(
            RecipientIdentifier.Single recipient, Function<RecipientId, Boolean> trustMethod
    ) throws UnregisteredRecipientException {
        RecipientId recipientId;
        try {
            recipientId = context.getRecipientHelper().resolveRecipient(recipient);
        } catch (IOException e) {
            return false;
        }
        final var updated = trustMethod.apply(recipientId);
        if (updated && this.isReceiving()) {
            context.getReceiveHelper().setNeedsToRetryFailedMessages(true);
        }
        return updated;
    }

    @Override
    public void addClosedListener(final Runnable listener) {
        synchronized (closedListeners) {
            closedListeners.add(listener);
        }
    }

    @Override
    public void close() {
        Thread thread;
        synchronized (messageHandlers) {
            weakHandlers.clear();
            messageHandlers.clear();
            thread = receiveThread;
            receiveThread = null;
        }
        if (thread != null) {
            stopReceiveThread(thread);
        }
        executor.shutdown();

        dependencies.getSignalWebSocket().disconnect();
        disposable.dispose();

        synchronized (closedListeners) {
            closedListeners.forEach(Runnable::run);
            closedListeners.clear();
        }

        if (account != null) {
            account.close();
        }
        account = null;
    }
}
