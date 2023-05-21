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
package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.AlreadyReceivingException;
import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.api.Configuration;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.DeviceLinkUrl;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupInviteLinkUrl;
import org.asamk.signal.manager.api.GroupNotFoundException;
import org.asamk.signal.manager.api.GroupSendingNotAllowedException;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.manager.api.InactiveGroupLinkException;
import org.asamk.signal.manager.api.InvalidDeviceLinkException;
import org.asamk.signal.manager.api.InvalidStickerException;
import org.asamk.signal.manager.api.InvalidUsernameException;
import org.asamk.signal.manager.api.LastGroupAdminException;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.NotAGroupMemberException;
import org.asamk.signal.manager.api.NotPrimaryDeviceException;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.PendingAdminApprovalException;
import org.asamk.signal.manager.api.Profile;
import org.asamk.signal.manager.api.ReceiveConfig;
import org.asamk.signal.manager.api.Recipient;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResult;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.manager.api.StickerPackInvalidException;
import org.asamk.signal.manager.api.StickerPackUrl;
import org.asamk.signal.manager.api.TextStyle;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.api.UpdateGroup;
import org.asamk.signal.manager.api.UpdateProfile;
import org.asamk.signal.manager.api.UserStatus;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.helper.AccountFileUpdater;
import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.storage.AttachmentStore;
import org.asamk.signal.manager.storage.AvatarStore;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.stickerPacks.JsonStickerPack;
import org.asamk.signal.manager.storage.stickerPacks.StickerPackStore;
import org.asamk.signal.manager.storage.stickers.StickerPack;
import org.asamk.signal.manager.util.AttachmentUtils;
import org.asamk.signal.manager.util.KeyUtils;
import org.asamk.signal.manager.util.MimeUtils;
import org.asamk.signal.manager.util.StickerUtils;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServicePreview;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final List<Runnable> addressChangedListeners = new ArrayList<>();
    private final CompositeDisposable disposable = new CompositeDisposable();

    public ManagerImpl(
            SignalAccount account,
            PathConfig pathConfig,
            AccountFileUpdater accountFileUpdater,
            ServiceEnvironmentConfig serviceEnvironmentConfig,
            String userAgent
    ) {
        this.account = account;

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
                account.getCredentialsProvider(),
                account.getSignalServiceDataStore(),
                executor,
                sessionLock);
        final var avatarStore = new AvatarStore(pathConfig.avatarsPath());
        final var attachmentStore = new AttachmentStore(pathConfig.attachmentsPath());
        final var stickerPackStore = new StickerPackStore(pathConfig.stickerPacksPath());

        this.context = new Context(account, new AccountFileUpdater() {
            @Override
            public void updateAccountIdentifiers(final String number, final ACI aci) {
                accountFileUpdater.updateAccountIdentifiers(number, aci);
                synchronized (addressChangedListeners) {
                    addressChangedListeners.forEach(Runnable::run);
                }
            }

            @Override
            public void removeAccount() {
                accountFileUpdater.removeAccount();
            }
        }, dependencies, avatarStore, attachmentStore, stickerPackStore);
        this.context.getAccountHelper().setUnregisteredListener(this::close);
        this.context.getReceiveHelper().setAuthenticationFailureListener(this::close);
        this.context.getReceiveHelper().setCaughtUpWithOldMessagesListener(() -> {
            synchronized (this) {
                this.notifyAll();
            }
        });
        disposable.add(account.getIdentityKeyStore().getIdentityChanges().subscribe(serviceId -> {
            logger.trace("Archiving old sessions for {}", serviceId);
            account.getAciSessionStore().archiveSessions(serviceId);
            account.getPniSessionStore().archiveSessions(serviceId);
            account.getSenderKeyStore().deleteSharedWith(serviceId);
            final var recipientId = account.getRecipientResolver().resolveRecipient(serviceId);
            final var profile = account.getProfileStore().getProfile(recipientId);
            if (profile != null) {
                account.getProfileStore()
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
        return account.getNumber();
    }

    public void checkAccountState() throws IOException {
        context.getAccountHelper().checkAccountState();
    }

    @Override
    public Map<String, UserStatus> getUserStatus(Set<String> numbers) throws IOException {
        final var canonicalizedNumbers = numbers.stream().collect(Collectors.toMap(n -> n, n -> {
            try {
                final var canonicalizedNumber = PhoneNumberFormatter.formatNumber(n, account.getNumber());
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
            final var user = registeredUsers.get(number);
            final var serviceId = user == null ? null : user.getServiceId();
            final var profile = serviceId == null
                    ? null
                    : context.getProfileHelper()
                            .getRecipientProfile(account.getRecipientResolver().resolveRecipient(serviceId));
            return new UserStatus(number.isEmpty() ? null : number,
                    serviceId == null ? null : serviceId.uuid(),
                    profile != null
                            && profile.getUnidentifiedAccessMode() == Profile.UnidentifiedAccessMode.UNRESTRICTED);
        }));
    }

    @Override
    public void updateAccountAttributes(String deviceName) throws IOException {
        if (deviceName != null) {
            context.getAccountHelper().setDeviceName(deviceName);
        }
        context.getAccountHelper().updateAccountAttributes();
        context.getAccountHelper().checkWhoAmiI();
    }

    @Override
    public Configuration getConfiguration() {
        final var configurationStore = account.getConfigurationStore();
        return Configuration.from(configurationStore);
    }

    @Override
    public void updateConfiguration(
            Configuration configuration
    ) throws NotPrimaryDeviceException {
        if (!account.isPrimaryDevice()) {
            throw new NotPrimaryDeviceException();
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
    public void updateProfile(UpdateProfile updateProfile) throws IOException {
        context.getProfileHelper()
                .setProfile(updateProfile.getGivenName(),
                        updateProfile.getFamilyName(),
                        updateProfile.getAbout(),
                        updateProfile.getAboutEmoji(),
                        updateProfile.isDeleteAvatar()
                                ? Optional.empty()
                                : updateProfile.getAvatar() == null ? null : Optional.of(updateProfile.getAvatar()),
                        updateProfile.getMobileCoinAddress());
        context.getSyncHelper().sendSyncFetchProfileMessage();
    }

    void refreshCurrentUsername() throws IOException, BaseUsernameException {
        context.getAccountHelper().refreshCurrentUsername();
    }

    @Override
    public String setUsername(final String username) throws IOException, InvalidUsernameException {
        try {
            return context.getAccountHelper().reserveUsername(username);
        } catch (BaseUsernameException e) {
            throw new InvalidUsernameException(e.getMessage() + " (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    @Override
    public void deleteUsername() throws IOException {
        context.getAccountHelper().deleteUsername();
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
        var identityKey = account.getAciIdentityKeyPair().getPrivateKey();
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
    public void addDeviceLink(DeviceLinkUrl linkUrl) throws IOException, InvalidDeviceLinkException {
        context.getAccountHelper().addDevice(linkUrl);
    }

    @Override
    public void setRegistrationLockPin(Optional<String> pin) throws IOException, NotPrimaryDeviceException {
        if (!account.isPrimaryDevice()) {
            throw new NotPrimaryDeviceException();
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
    public List<Group> getGroups() {
        return account.getGroupStore().getGroups().stream().map(this::toGroup).toList();
    }

    private Group toGroup(final GroupInfo groupInfo) {
        if (groupInfo == null) {
            return null;
        }

        return Group.from(groupInfo, account.getRecipientAddressResolver(), account.getSelfRecipientId());
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
        final var group = context.getGroupHelper().getGroup(groupId);
        if (group.isMember(account.getSelfRecipientId())) {
            throw new IOException(
                    "The local group information cannot be removed, as the user is still a member of the group");
        }
        context.getGroupHelper().deleteGroup(groupId);
    }

    @Override
    public Pair<GroupId, SendGroupMessageResults> createGroup(
            String name, Set<RecipientIdentifier.Single> members, String avatarFile
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
                        updateGroup.getBanMembers() == null
                                ? null
                                : context.getRecipientHelper().resolveRecipients(updateGroup.getBanMembers()),
                        updateGroup.getUnbanMembers() == null
                                ? null
                                : context.getRecipientHelper().resolveRecipients(updateGroup.getUnbanMembers()),
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
    ) throws IOException, InactiveGroupLinkException, PendingAdminApprovalException {
        return context.getGroupHelper().joinGroup(inviteLinkUrl);
    }

    private SendMessageResults sendMessage(
            SignalServiceDataMessage.Builder messageBuilder, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        return sendMessage(messageBuilder, recipients, Optional.empty());
    }

    private SendMessageResults sendMessage(
            SignalServiceDataMessage.Builder messageBuilder,
            Set<RecipientIdentifier> recipients,
            Optional<Long> editTargetTimestamp
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        var results = new HashMap<RecipientIdentifier, List<SendMessageResult>>();
        long timestamp = System.currentTimeMillis();
        messageBuilder.withTimestamp(timestamp);
        for (final var recipient : recipients) {
            if (recipient instanceof RecipientIdentifier.Single single) {
                try {
                    final var recipientId = context.getRecipientHelper().resolveRecipient(single);
                    final var result = context.getSendHelper()
                            .sendMessage(messageBuilder, recipientId, editTargetTimestamp);
                    results.put(recipient, List.of(toSendMessageResult(result)));
                } catch (UnregisteredRecipientException e) {
                    results.put(recipient,
                            List.of(SendMessageResult.unregisteredFailure(single.toPartialRecipientAddress())));
                }
            } else if (recipient instanceof RecipientIdentifier.NoteToSelf) {
                final var result = context.getSendHelper().sendSelfMessage(messageBuilder, editTargetTimestamp);
                results.put(recipient, List.of(toSendMessageResult(result)));
            } else if (recipient instanceof RecipientIdentifier.Group group) {
                final var result = context.getSendHelper()
                        .sendAsGroupMessage(messageBuilder, group.groupId(), editTargetTimestamp);
                results.put(recipient, result.stream().map(this::toSendMessageResult).toList());
            }
        }
        return new SendMessageResults(timestamp, results);
    }

    private SendMessageResult toSendMessageResult(final org.whispersystems.signalservice.api.messages.SendMessageResult result) {
        return SendMessageResult.from(result, account.getRecipientResolver(), account.getRecipientAddressResolver());
    }

    private SendMessageResults sendTypingMessage(
            SignalServiceTypingMessage.Action action, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        var results = new HashMap<RecipientIdentifier, List<SendMessageResult>>();
        final var timestamp = System.currentTimeMillis();
        for (var recipient : recipients) {
            if (recipient instanceof RecipientIdentifier.Single single) {
                final var message = new SignalServiceTypingMessage(action, timestamp, Optional.empty());
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
    ) {
        final var timestamp = System.currentTimeMillis();
        var receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ,
                messageIds,
                timestamp);

        return sendReceiptMessage(sender, timestamp, receiptMessage);
    }

    @Override
    public SendMessageResults sendViewedReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    ) {
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
    ) {
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
        final var selfProfile = context.getProfileHelper().getSelfProfile();
        if (selfProfile == null || selfProfile.getDisplayName().isEmpty()) {
            logger.warn(
                    "No profile name set. When sending a message it's recommended to set a profile name with the updateProfile command. This may become mandatory in the future.");
        }
        final var messageBuilder = SignalServiceDataMessage.newBuilder();
        applyMessage(messageBuilder, message);
        return sendMessage(messageBuilder, recipients);
    }

    @Override
    public SendMessageResults sendEditMessage(
            Message message, Set<RecipientIdentifier> recipients, long editTargetTimestamp
    ) throws IOException, AttachmentInvalidException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException, UnregisteredRecipientException, InvalidStickerException {
        final var messageBuilder = SignalServiceDataMessage.newBuilder();
        applyMessage(messageBuilder, message);
        return sendMessage(messageBuilder, recipients, Optional.of(editTargetTimestamp));
    }

    private void applyMessage(
            final SignalServiceDataMessage.Builder messageBuilder, final Message message
    ) throws AttachmentInvalidException, IOException, UnregisteredRecipientException, InvalidStickerException {
        if (message.messageText().length() > 2000) {
            final var messageBytes = message.messageText().getBytes(StandardCharsets.UTF_8);
            final var textAttachment = AttachmentUtils.createAttachmentStream(new StreamDetails(new ByteArrayInputStream(
                    messageBytes), MimeUtils.LONG_TEXT, messageBytes.length), Optional.empty());
            messageBuilder.withBody(message.messageText().substring(0, 2000));
            messageBuilder.withAttachment(context.getAttachmentHelper().uploadAttachment(textAttachment));
        } else {
            messageBuilder.withBody(message.messageText());
        }
        if (message.attachments().size() > 0) {
            messageBuilder.withAttachments(context.getAttachmentHelper().uploadAttachments(message.attachments()));
        }
        if (message.mentions().size() > 0) {
            messageBuilder.withMentions(resolveMentions(message.mentions()));
        }
        if (message.textStyles().size() > 0) {
            messageBuilder.withBodyRanges(message.textStyles().stream().map(TextStyle::toBodyRange).toList());
        }
        if (message.quote().isPresent()) {
            final var quote = message.quote().get();
            messageBuilder.withQuote(new SignalServiceDataMessage.Quote(quote.timestamp(),
                    context.getRecipientHelper()
                            .resolveSignalServiceAddress(context.getRecipientHelper().resolveRecipient(quote.author()))
                            .getServiceId(),
                    quote.message(),
                    List.of(),
                    resolveMentions(quote.mentions()),
                    SignalServiceDataMessage.Quote.Type.NORMAL,
                    quote.textStyles().stream().map(TextStyle::toBodyRange).toList()));
        }
        if (message.sticker().isPresent()) {
            final var sticker = message.sticker().get();
            final var packId = StickerPackId.deserialize(sticker.packId());
            final var stickerId = sticker.stickerId();

            final var stickerPack = context.getAccount().getStickerStore().getStickerPack(packId);
            if (stickerPack == null) {
                throw new InvalidStickerException("Sticker pack not found");
            }
            final var manifest = context.getStickerHelper().getOrRetrieveStickerPack(packId, stickerPack.packKey());
            if (manifest.stickers().size() <= stickerId) {
                throw new InvalidStickerException("Sticker id not part of this pack");
            }
            final var manifestSticker = manifest.stickers().get(stickerId);
            final var streamDetails = context.getStickerPackStore().retrieveSticker(packId, stickerId);
            if (streamDetails == null) {
                throw new InvalidStickerException("Missing local sticker file");
            }
            messageBuilder.withSticker(new SignalServiceDataMessage.Sticker(packId.serialize(),
                    stickerPack.packKey(),
                    stickerId,
                    manifestSticker.emoji(),
                    AttachmentUtils.createAttachmentStream(streamDetails, Optional.empty())));
        }
        if (message.previews().size() > 0) {
            final var previews = new ArrayList<SignalServicePreview>(message.previews().size());
            for (final var p : message.previews()) {
                final var image = p.image().isPresent() ? context.getAttachmentHelper()
                        .uploadAttachment(p.image().get()) : null;
                previews.add(new SignalServicePreview(p.url(),
                        p.title(),
                        p.description(),
                        0,
                        Optional.ofNullable(image)));
            }
            messageBuilder.withPreviews(previews);
        }
        if (message.storyReply().isPresent()) {
            final var storyReply = message.storyReply().get();
            final var authorServiceId = context.getRecipientHelper()
                    .resolveSignalServiceAddress(context.getRecipientHelper().resolveRecipient(storyReply.author()))
                    .getServiceId();
            messageBuilder.withStoryContext(new SignalServiceDataMessage.StoryContext(authorServiceId,
                    storyReply.timestamp()));
        }
    }

    private ArrayList<SignalServiceDataMessage.Mention> resolveMentions(final List<Message.Mention> mentionList) throws UnregisteredRecipientException {
        final var mentions = new ArrayList<SignalServiceDataMessage.Mention>();
        for (final var m : mentionList) {
            final var recipientId = context.getRecipientHelper().resolveRecipient(m.recipient());
            mentions.add(new SignalServiceDataMessage.Mention(context.getRecipientHelper()
                    .resolveSignalServiceAddress(recipientId)
                    .getServiceId(), m.start(), m.length()));
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
            if (recipient instanceof RecipientIdentifier.Uuid u) {
                account.getMessageSendLogStore()
                        .deleteEntryForRecipientNonGroup(targetSentTimestamp, ServiceId.from(u.uuid()));
            } else if (recipient instanceof RecipientIdentifier.Single r) {
                try {
                    final var recipientId = context.getRecipientHelper().resolveRecipient(r);
                    final var address = account.getRecipientAddressResolver().resolveRecipientAddress(recipientId);
                    if (address.serviceId().isPresent()) {
                        account.getMessageSendLogStore()
                                .deleteEntryForRecipientNonGroup(targetSentTimestamp, address.serviceId().get());
                    }
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
            Set<RecipientIdentifier> recipients,
            final boolean isStory
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException, UnregisteredRecipientException {
        var targetAuthorRecipientId = context.getRecipientHelper().resolveRecipient(targetAuthor);
        final var authorServiceId = context.getRecipientHelper()
                .resolveSignalServiceAddress(targetAuthorRecipientId)
                .getServiceId();
        var reaction = new SignalServiceDataMessage.Reaction(emoji, remove, authorServiceId, targetSentTimestamp);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withReaction(reaction);
        if (isStory) {
            messageBuilder.withStoryContext(new SignalServiceDataMessage.StoryContext(authorServiceId,
                    targetSentTimestamp));
        }
        return sendMessage(messageBuilder, recipients);
    }

    @Override
    public SendMessageResults sendPaymentNotificationMessage(
            byte[] receipt, String note, RecipientIdentifier.Single recipient
    ) throws IOException {
        final var paymentNotification = new SignalServiceDataMessage.PaymentNotification(receipt, note);
        final var payment = new SignalServiceDataMessage.Payment(paymentNotification, null);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withPayment(payment);
        try {
            return sendMessage(messageBuilder, Set.of(recipient));
        } catch (NotAGroupMemberException | GroupNotFoundException | GroupSendingNotAllowedException e) {
            throw new AssertionError(e);
        }
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
                final var serviceId = context.getAccount()
                        .getRecipientAddressResolver()
                        .resolveRecipientAddress(recipientId)
                        .serviceId();
                if (serviceId.isPresent()) {
                    account.getAciSessionStore().deleteAllSessions(serviceId.get());
                }
            }
        }
    }

    @Override
    public void deleteRecipient(final RecipientIdentifier.Single recipient) {
        final var recipientIdOptional = context.getRecipientHelper().resolveRecipientOptional(recipient);
        if (recipientIdOptional.isPresent()) {
            account.removeRecipient(recipientIdOptional.get());
        }
    }

    @Override
    public void deleteContact(final RecipientIdentifier.Single recipient) {
        final var recipientIdOptional = context.getRecipientHelper().resolveRecipientOptional(recipient);
        if (recipientIdOptional.isPresent()) {
            account.getContactStore().deleteContact(recipientIdOptional.get());
        }
    }

    @Override
    public void setContactName(
            RecipientIdentifier.Single recipient, String givenName, final String familyName
    ) throws NotPrimaryDeviceException, UnregisteredRecipientException {
        if (!account.isPrimaryDevice()) {
            throw new NotPrimaryDeviceException();
        }
        context.getContactHelper()
                .setContactName(context.getRecipientHelper().resolveRecipient(recipient), givenName, familyName);
    }

    @Override
    public void setContactsBlocked(
            Collection<RecipientIdentifier.Single> recipients, boolean blocked
    ) throws NotPrimaryDeviceException, IOException, UnregisteredRecipientException {
        if (!account.isPrimaryDevice()) {
            throw new NotPrimaryDeviceException();
        }
        if (recipients.size() == 0) {
            return;
        }
        final var recipientIds = context.getRecipientHelper().resolveRecipients(recipients);
        final var selfRecipientId = account.getSelfRecipientId();
        boolean shouldRotateProfileKey = false;
        for (final var recipientId : recipientIds) {
            if (context.getContactHelper().isContactBlocked(recipientId) == blocked) {
                continue;
            }
            context.getContactHelper().setContactBlocked(recipientId, blocked);
            // if we don't have a common group with the blocked contact we need to rotate the profile key
            shouldRotateProfileKey = blocked && (
                    shouldRotateProfileKey || account.getGroupStore()
                            .getGroups()
                            .stream()
                            .noneMatch(g -> g.isMember(selfRecipientId) && g.isMember(recipientId))
            );
        }
        if (shouldRotateProfileKey) {
            context.getProfileHelper().rotateProfileKey();
        }
        context.getSyncHelper().sendBlockedList();
    }

    @Override
    public void setGroupsBlocked(
            final Collection<GroupId> groupIds, final boolean blocked
    ) throws GroupNotFoundException, NotPrimaryDeviceException, IOException {
        if (!account.isPrimaryDevice()) {
            throw new NotPrimaryDeviceException();
        }
        if (groupIds.size() == 0) {
            return;
        }
        boolean shouldRotateProfileKey = false;
        for (final var groupId : groupIds) {
            if (context.getGroupHelper().isGroupBlocked(groupId) == blocked) {
                continue;
            }
            context.getGroupHelper().setGroupBlocked(groupId, blocked);
            shouldRotateProfileKey = blocked;
        }
        if (shouldRotateProfileKey) {
            context.getProfileHelper().rotateProfileKey();
        }
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

        var sticker = new StickerPack(packId, packKey);
        account.getStickerStore().addStickerPack(sticker);

        return new StickerPackUrl(packId, packKey);
    }

    @Override
    public List<org.asamk.signal.manager.api.StickerPack> getStickerPacks() {
        final var stickerPackStore = context.getStickerPackStore();
        return account.getStickerStore().getStickerPacks().stream().map(pack -> {
            if (stickerPackStore.existsStickerPack(pack.packId())) {
                try {
                    final var manifest = stickerPackStore.retrieveManifest(pack.packId());
                    return new org.asamk.signal.manager.api.StickerPack(pack.packId(),
                            new StickerPackUrl(pack.packId(), pack.packKey()),
                            pack.isInstalled(),
                            manifest.title(),
                            manifest.author(),
                            Optional.ofNullable(manifest.cover() == null ? null : manifest.cover().toApi()),
                            manifest.stickers().stream().map(JsonStickerPack.JsonSticker::toApi).toList());
                } catch (Exception e) {
                    logger.warn("Failed to read local sticker pack manifest: {}", e.getMessage(), e);
                }
            }

            return new org.asamk.signal.manager.api.StickerPack(pack.packId(), pack.packKey(), pack.isInstalled());
        }).toList();
    }

    @Override
    public void requestAllSyncData() throws IOException {
        context.getSyncHelper().requestAllSyncData();
        retrieveRemoteStorage();
    }

    void retrieveRemoteStorage() throws IOException {
        context.getStorageHelper().readDataFromStorage();
    }

    @Override
    public void addReceiveHandler(final ReceiveMessageHandler handler, final boolean isWeakListener) {
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
        if (receiveThread != null || isReceivingSynchronous) {
            return;
        }
        receiveThread = new Thread(() -> {
            logger.debug("Starting receiving messages");
            context.getReceiveHelper().receiveMessagesContinuously(this::passReceivedMessageToHandlers);
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

    private void passReceivedMessageToHandlers(MessageEnvelope envelope, Throwable e) {
        synchronized (messageHandlers) {
            Stream.concat(messageHandlers.stream(), weakHandlers.stream()).forEach(h -> {
                try {
                    h.handleMessage(envelope, e);
                } catch (Throwable ex) {
                    logger.warn("Message handler failed, ignoring", ex);
                }
            });
        }
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
        if (context.getReceiveHelper().requestStopReceiveMessages()) {
            logger.debug("Receive stop requested, interrupting read from server.");
            thread.interrupt();
        }
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
    public void receiveMessages(
            Optional<Duration> timeout, Optional<Integer> maxMessages, ReceiveMessageHandler handler
    ) throws IOException, AlreadyReceivingException {
        receiveMessages(timeout.orElse(Duration.ofMinutes(1)), timeout.isPresent(), maxMessages.orElse(null), handler);
    }

    private void receiveMessages(
            Duration timeout, boolean returnOnTimeout, Integer maxMessages, ReceiveMessageHandler handler
    ) throws IOException, AlreadyReceivingException {
        synchronized (messageHandlers) {
            if (isReceiving()) {
                throw new AlreadyReceivingException("Already receiving message.");
            }
            isReceivingSynchronous = true;
            receiveThread = Thread.currentThread();
        }
        try {
            context.getReceiveHelper().receiveMessages(timeout, returnOnTimeout, maxMessages, (envelope, e) -> {
                passReceivedMessageToHandlers(envelope, e);
                handler.handleMessage(envelope, e);
            });
        } finally {
            synchronized (messageHandlers) {
                receiveThread = null;
                isReceivingSynchronous = false;
                if (messageHandlers.size() > 0) {
                    startReceiveThreadIfRequired();
                }
            }
        }
    }

    @Override
    public void setReceiveConfig(final ReceiveConfig receiveConfig) {
        context.getReceiveHelper().setReceiveConfig(receiveConfig);
    }

    @Override
    public boolean isContactBlocked(final RecipientIdentifier.Single recipient) {
        final RecipientId recipientId;
        try {
            recipientId = context.getRecipientHelper().resolveRecipient(recipient);
        } catch (UnregisteredRecipientException e) {
            return false;
        }
        return context.getContactHelper().isContactBlocked(recipientId);
    }

    @Override
    public void sendContacts() throws IOException {
        context.getSyncHelper().sendContacts();
    }

    @Override
    public List<Recipient> getRecipients(
            boolean onlyContacts,
            Optional<Boolean> blocked,
            Collection<RecipientIdentifier.Single> recipients,
            Optional<String> name
    ) {
        final var recipientIds = recipients.stream().map(a -> {
            try {
                return context.getRecipientHelper().resolveRecipient(a);
            } catch (UnregisteredRecipientException e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!recipients.isEmpty() && recipientIds.isEmpty()) {
            return List.of();
        }
        // refresh profiles of explicitly given recipients
        context.getProfileHelper().refreshRecipientProfiles(recipientIds);
        return account.getRecipientStore()
                .getRecipients(onlyContacts, blocked, recipientIds, name)
                .stream()
                .map(s -> new Recipient(s.getRecipientId(),
                        s.getAddress().toApiRecipientAddress(),
                        s.getContact(),
                        s.getProfileKey(),
                        s.getExpiringProfileKeyCredential(),
                        s.getProfile()))
                .toList();
    }

    @Override
    public String getContactOrProfileName(RecipientIdentifier.Single recipient) {
        final RecipientId recipientId;
        try {
            recipientId = context.getRecipientHelper().resolveRecipient(recipient);
        } catch (UnregisteredRecipientException e) {
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

        final var address = account.getRecipientAddressResolver()
                .resolveRecipientAddress(account.getRecipientResolver().resolveRecipient(identityInfo.getServiceId()));
        final var scannableFingerprint = context.getIdentityHelper()
                .computeSafetyNumberForScanning(identityInfo.getServiceId(), identityInfo.getIdentityKey());
        return new Identity(address.toApiRecipientAddress(),
                identityInfo.getIdentityKey(),
                context.getIdentityHelper()
                        .computeSafetyNumber(identityInfo.getServiceId(), identityInfo.getIdentityKey()),
                scannableFingerprint == null ? null : scannableFingerprint.getSerialized(),
                identityInfo.getTrustLevel(),
                identityInfo.getDateAddedTimestamp());
    }

    @Override
    public List<Identity> getIdentities(RecipientIdentifier.Single recipient) {
        ServiceId serviceId;
        try {
            final var address = account.getRecipientAddressResolver()
                    .resolveRecipientAddress(context.getRecipientHelper().resolveRecipient(recipient));
            if (address.serviceId().isEmpty()) {
                return List.of();
            }
            serviceId = address.serviceId().get();
        } catch (UnregisteredRecipientException e) {
            return List.of();
        }
        final var identity = account.getIdentityKeyStore().getIdentityInfo(serviceId);
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
        final var recipientId = context.getRecipientHelper().resolveRecipient(recipient);
        final var updated = trustMethod.apply(recipientId);
        if (updated && this.isReceiving()) {
            context.getReceiveHelper().setNeedsToRetryFailedMessages(true);
        }
        return updated;
    }

    @Override
    public void addAddressChangedListener(final Runnable listener) {
        synchronized (addressChangedListeners) {
            addressChangedListeners.add(listener);
        }
    }

    @Override
    public void addClosedListener(final Runnable listener) {
        synchronized (closedListeners) {
            closedListeners.add(listener);
        }
    }

    @Override
    public InputStream retrieveAttachment(final String id) throws IOException {
        return context.getAttachmentHelper().retrieveAttachment(id).getStream();
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

        if (account != null) {
            account.close();
        }

        synchronized (closedListeners) {
            closedListeners.forEach(Runnable::run);
            closedListeners.clear();
        }

        account = null;
    }
}
