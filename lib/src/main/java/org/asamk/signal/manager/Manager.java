/*
  Copyright (C) 2015-2021 AsamK and contributors

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

import org.asamk.signal.manager.actions.HandleAction;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.helper.AttachmentHelper;
import org.asamk.signal.manager.helper.ContactHelper;
import org.asamk.signal.manager.helper.GroupHelper;
import org.asamk.signal.manager.helper.GroupV2Helper;
import org.asamk.signal.manager.helper.IncomingMessageHandler;
import org.asamk.signal.manager.helper.PinHelper;
import org.asamk.signal.manager.helper.ProfileHelper;
import org.asamk.signal.manager.helper.SendHelper;
import org.asamk.signal.manager.helper.SyncHelper;
import org.asamk.signal.manager.helper.UnidentifiedAccessHelper;
import org.asamk.signal.manager.jobs.Context;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;
import org.asamk.signal.manager.storage.messageCache.CachedMessage;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.stickers.Sticker;
import org.asamk.signal.manager.storage.stickers.StickerPackId;
import org.asamk.signal.manager.util.KeyUtils;
import org.asamk.signal.manager.util.StickerUtils;
import org.asamk.signal.manager.util.Utils;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.FingerprintParsingException;
import org.whispersystems.libsignal.fingerprint.FingerprintVersionMismatchException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.asamk.signal.manager.config.ServiceConfig.capabilities;

public class Manager implements Closeable {

    private final static Logger logger = LoggerFactory.getLogger(Manager.class);

    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final SignalDependencies dependencies;

    private SignalAccount account;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final ProfileHelper profileHelper;
    private final PinHelper pinHelper;
    private final SendHelper sendHelper;
    private final SyncHelper syncHelper;
    private final AttachmentHelper attachmentHelper;
    private final GroupHelper groupHelper;
    private final ContactHelper contactHelper;
    private final IncomingMessageHandler incomingMessageHandler;

    private final Context context;

    Manager(
            SignalAccount account,
            PathConfig pathConfig,
            ServiceEnvironmentConfig serviceEnvironmentConfig,
            String userAgent
    ) {
        this.account = account;
        this.serviceEnvironmentConfig = serviceEnvironmentConfig;

        final var credentialsProvider = new DynamicCredentialsProvider(account.getUuid(),
                account.getUsername(),
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
        this.dependencies = new SignalDependencies(account.getSelfAddress(),
                serviceEnvironmentConfig,
                userAgent,
                credentialsProvider,
                account.getSignalProtocolStore(),
                executor,
                sessionLock);
        final var avatarStore = new AvatarStore(pathConfig.getAvatarsPath());
        final var attachmentStore = new AttachmentStore(pathConfig.getAttachmentsPath());
        final var stickerPackStore = new StickerPackStore(pathConfig.getStickerPacksPath());

        this.attachmentHelper = new AttachmentHelper(dependencies, attachmentStore);
        this.pinHelper = new PinHelper(dependencies.getKeyBackupService());
        final var unidentifiedAccessHelper = new UnidentifiedAccessHelper(account::getProfileKey,
                account.getProfileStore()::getProfileKey,
                this::getRecipientProfile,
                this::getSenderCertificate);
        this.profileHelper = new ProfileHelper(account,
                dependencies,
                avatarStore,
                account.getProfileStore()::getProfileKey,
                unidentifiedAccessHelper::getAccessFor,
                dependencies::getProfileService,
                dependencies::getMessageReceiver,
                this::resolveSignalServiceAddress);
        final GroupV2Helper groupV2Helper = new GroupV2Helper(profileHelper::getRecipientProfileKeyCredential,
                this::getRecipientProfile,
                account::getSelfRecipientId,
                dependencies.getGroupsV2Operations(),
                dependencies.getGroupsV2Api(),
                this::resolveSignalServiceAddress);
        this.sendHelper = new SendHelper(account,
                dependencies,
                unidentifiedAccessHelper,
                this::resolveSignalServiceAddress,
                this::resolveRecipient,
                this::handleIdentityFailure,
                this::getGroup,
                this::refreshRegisteredUser);
        this.groupHelper = new GroupHelper(account,
                dependencies,
                attachmentHelper,
                sendHelper,
                groupV2Helper,
                avatarStore,
                this::resolveSignalServiceAddress,
                this::resolveRecipient);
        this.contactHelper = new ContactHelper(account);
        this.syncHelper = new SyncHelper(account,
                attachmentHelper,
                sendHelper,
                groupHelper,
                avatarStore,
                this::resolveSignalServiceAddress,
                this::resolveRecipient);

        this.context = new Context(account,
                dependencies.getAccountManager(),
                dependencies.getMessageReceiver(),
                stickerPackStore,
                sendHelper,
                groupHelper,
                syncHelper,
                profileHelper);
        var jobExecutor = new JobExecutor(context);

        this.incomingMessageHandler = new IncomingMessageHandler(account,
                dependencies,
                this::resolveRecipient,
                groupHelper,
                contactHelper,
                attachmentHelper,
                syncHelper,
                jobExecutor);
    }

    public String getUsername() {
        return account.getUsername();
    }

    public RecipientId getSelfRecipientId() {
        return account.getSelfRecipientId();
    }

    private IdentityKeyPair getIdentityKeyPair() {
        return account.getIdentityKeyPair();
    }

    public int getDeviceId() {
        return account.getDeviceId();
    }

    public static Manager init(
            String username,
            File settingsPath,
            ServiceEnvironment serviceEnvironment,
            String userAgent,
            final TrustNewIdentity trustNewIdentity
    ) throws IOException, NotRegisteredException {
        var pathConfig = PathConfig.createDefault(settingsPath);

        if (!SignalAccount.userExists(pathConfig.getDataPath(), username)) {
            throw new NotRegisteredException();
        }

        var account = SignalAccount.load(pathConfig.getDataPath(), username, true, trustNewIdentity);

        if (!account.isRegistered()) {
            throw new NotRegisteredException();
        }

        final var serviceEnvironmentConfig = ServiceConfig.getServiceEnvironmentConfig(serviceEnvironment, userAgent);

        return new Manager(account, pathConfig, serviceEnvironmentConfig, userAgent);
    }

    public static List<String> getAllLocalUsernames(File settingsPath) {
        var pathConfig = PathConfig.createDefault(settingsPath);
        final var dataPath = pathConfig.getDataPath();
        final var files = dataPath.listFiles();

        if (files == null) {
            return List.of();
        }

        return Arrays.stream(files)
                .filter(File::isFile)
                .map(File::getName)
                .filter(file -> PhoneNumberFormatter.isValidNumber(file, null))
                .collect(Collectors.toList());
    }

    public void checkAccountState() throws IOException {
        if (account.getLastReceiveTimestamp() == 0) {
            logger.info("The Signal protocol expects that incoming messages are regularly received.");
        } else {
            var diffInMilliseconds = System.currentTimeMillis() - account.getLastReceiveTimestamp();
            long days = TimeUnit.DAYS.convert(diffInMilliseconds, TimeUnit.MILLISECONDS);
            if (days > 7) {
                logger.warn(
                        "Messages have been last received {} days ago. The Signal protocol expects that incoming messages are regularly received.",
                        days);
            }
        }
        if (dependencies.getAccountManager().getPreKeysCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
            refreshPreKeys();
        }
        if (account.getUuid() == null) {
            account.setUuid(dependencies.getAccountManager().getOwnUuid());
        }
        updateAccountAttributes();
    }

    /**
     * This is used for checking a set of phone numbers for registration on Signal
     *
     * @param numbers The set of phone number in question
     * @return A map of numbers to canonicalized number and uuid. If a number is not registered the uuid is null.
     * @throws IOException if its unable to get the contacts to check if they're registered
     */
    public Map<String, Pair<String, UUID>> areUsersRegistered(Set<String> numbers) throws IOException {
        Map<String, String> canonicalizedNumbers = numbers.stream().collect(Collectors.toMap(n -> n, n -> {
            try {
                return canonicalizePhoneNumber(n);
            } catch (InvalidNumberException e) {
                return "";
            }
        }));

        // Note "registeredUsers" has no optionals. It only gives us info on users who are registered
        var registeredUsers = getRegisteredUsers(canonicalizedNumbers.values()
                .stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet()));

        return numbers.stream().collect(Collectors.toMap(n -> n, n -> {
            final var number = canonicalizedNumbers.get(n);
            final var uuid = registeredUsers.get(number);
            return new Pair<>(number.isEmpty() ? null : number, uuid);
        }));
    }

    public void updateAccountAttributes() throws IOException {
        dependencies.getAccountManager()
                .setAccountAttributes(account.getEncryptedDeviceName(),
                        null,
                        account.getLocalRegistrationId(),
                        true,
                        // set legacy pin only if no KBS master key is set
                        account.getPinMasterKey() == null ? account.getRegistrationLockPin() : null,
                        account.getPinMasterKey() == null ? null : account.getPinMasterKey().deriveRegistrationLock(),
                        account.getSelfUnidentifiedAccessKey(),
                        account.isUnrestrictedUnidentifiedAccess(),
                        capabilities,
                        account.isDiscoverableByPhoneNumber());
    }

    /**
     * @param givenName  if null, the previous givenName will be kept
     * @param familyName if null, the previous familyName will be kept
     * @param about      if null, the previous about text will be kept
     * @param aboutEmoji if null, the previous about emoji will be kept
     * @param avatar     if avatar is null the image from the local avatar store is used (if present),
     */
    public void setProfile(
            String givenName, final String familyName, String about, String aboutEmoji, Optional<File> avatar
    ) throws IOException {
        profileHelper.setProfile(givenName, familyName, about, aboutEmoji, avatar);
        syncHelper.sendSyncFetchProfileMessage();
    }

    public void unregister() throws IOException {
        // When setting an empty GCM id, the Signal-Server also sets the fetchesMessages property to false.
        // If this is the master device, other users can't send messages to this number anymore.
        // If this is a linked device, other users can still send messages, but this device doesn't receive them anymore.
        dependencies.getAccountManager().setGcmId(Optional.absent());

        account.setRegistered(false);
    }

    public void deleteAccount() throws IOException {
        dependencies.getAccountManager().deleteAccount();

        account.setRegistered(false);
    }

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
            return new Device(d.getId(), deviceName, d.getCreated(), d.getLastSeen());
        }).collect(Collectors.toList());
    }

    public void removeLinkedDevices(int deviceId) throws IOException {
        dependencies.getAccountManager().removeDevice(deviceId);
        var devices = dependencies.getAccountManager().getDevices();
        account.setMultiDevice(devices.size() > 1);
    }

    public void addDeviceLink(URI linkUri) throws IOException, InvalidKeyException {
        var info = DeviceLinkInfo.parseDeviceLinkUri(linkUri);

        addDevice(info.deviceIdentifier, info.deviceKey);
    }

    private void addDevice(String deviceIdentifier, ECPublicKey deviceKey) throws IOException, InvalidKeyException {
        var identityKeyPair = getIdentityKeyPair();
        var verificationCode = dependencies.getAccountManager().getNewDeviceVerificationCode();

        dependencies.getAccountManager()
                .addDevice(deviceIdentifier,
                        deviceKey,
                        identityKeyPair,
                        Optional.of(account.getProfileKey().serialize()),
                        verificationCode);
        account.setMultiDevice(true);
    }

    public void setRegistrationLockPin(Optional<String> pin) throws IOException, UnauthenticatedResponseException {
        if (!account.isMasterDevice()) {
            throw new RuntimeException("Only master device can set a PIN");
        }
        if (pin.isPresent()) {
            final var masterKey = account.getPinMasterKey() != null
                    ? account.getPinMasterKey()
                    : KeyUtils.createMasterKey();

            pinHelper.setRegistrationLockPin(pin.get(), masterKey);

            account.setRegistrationLockPin(pin.get(), masterKey);
        } else {
            // Remove KBS Pin
            pinHelper.removeRegistrationLockPin();

            account.setRegistrationLockPin(null, null);
        }
    }

    void refreshPreKeys() throws IOException {
        var oneTimePreKeys = generatePreKeys();
        final var identityKeyPair = getIdentityKeyPair();
        var signedPreKeyRecord = generateSignedPreKey(identityKeyPair);

        dependencies.getAccountManager().setPreKeys(identityKeyPair.getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
    }

    private List<PreKeyRecord> generatePreKeys() {
        final var offset = account.getPreKeyIdOffset();

        var records = KeyUtils.generatePreKeyRecords(offset, ServiceConfig.PREKEY_BATCH_SIZE);
        account.addPreKeys(records);

        return records;
    }

    private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair) {
        final var signedPreKeyId = account.getNextSignedPreKeyId();

        var record = KeyUtils.generateSignedPreKeyRecord(identityKeyPair, signedPreKeyId);
        account.addSignedPreKey(record);

        return record;
    }

    public Profile getRecipientProfile(RecipientId recipientId) {
        return profileHelper.getRecipientProfile(recipientId);
    }

    public List<GroupInfo> getGroups() {
        return account.getGroupStore().getGroups();
    }

    public SendGroupMessageResults quitGroup(
            GroupId groupId, Set<RecipientIdentifier.Single> groupAdmins
    ) throws GroupNotFoundException, IOException, NotAGroupMemberException, LastGroupAdminException {
        final var newAdmins = getRecipientIds(groupAdmins);
        return groupHelper.quitGroup(groupId, newAdmins);
    }

    public void deleteGroup(GroupId groupId) throws IOException {
        groupHelper.deleteGroup(groupId);
    }

    public Pair<GroupId, SendGroupMessageResults> createGroup(
            String name, Set<RecipientIdentifier.Single> members, File avatarFile
    ) throws IOException, AttachmentInvalidException {
        return groupHelper.createGroup(name, members == null ? null : getRecipientIds(members), avatarFile);
    }

    public SendGroupMessageResults updateGroup(
            GroupId groupId,
            String name,
            String description,
            Set<RecipientIdentifier.Single> members,
            Set<RecipientIdentifier.Single> removeMembers,
            Set<RecipientIdentifier.Single> admins,
            Set<RecipientIdentifier.Single> removeAdmins,
            boolean resetGroupLink,
            GroupLinkState groupLinkState,
            GroupPermission addMemberPermission,
            GroupPermission editDetailsPermission,
            File avatarFile,
            Integer expirationTimer,
            Boolean isAnnouncementGroup
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, GroupSendingNotAllowedException {
        return groupHelper.updateGroup(groupId,
                name,
                description,
                members == null ? null : getRecipientIds(members),
                removeMembers == null ? null : getRecipientIds(removeMembers),
                admins == null ? null : getRecipientIds(admins),
                removeAdmins == null ? null : getRecipientIds(removeAdmins),
                resetGroupLink,
                groupLinkState,
                addMemberPermission,
                editDetailsPermission,
                avatarFile,
                expirationTimer,
                isAnnouncementGroup);
    }

    public Pair<GroupId, SendGroupMessageResults> joinGroup(
            GroupInviteLinkUrl inviteLinkUrl
    ) throws IOException, GroupLinkNotActiveException {
        return groupHelper.joinGroup(inviteLinkUrl);
    }

    public SendMessageResults sendMessage(
            SignalServiceDataMessage.Builder messageBuilder, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        var results = new HashMap<RecipientIdentifier, List<SendMessageResult>>();
        long timestamp = System.currentTimeMillis();
        messageBuilder.withTimestamp(timestamp);
        for (final var recipient : recipients) {
            if (recipient instanceof RecipientIdentifier.Single) {
                final var recipientId = resolveRecipient((RecipientIdentifier.Single) recipient);
                final var result = sendHelper.sendMessage(messageBuilder, recipientId);
                results.put(recipient, List.of(result));
            } else if (recipient instanceof RecipientIdentifier.NoteToSelf) {
                final var result = sendHelper.sendSelfMessage(messageBuilder);
                results.put(recipient, List.of(result));
            } else if (recipient instanceof RecipientIdentifier.Group) {
                final var groupId = ((RecipientIdentifier.Group) recipient).groupId;
                final var result = sendHelper.sendAsGroupMessage(messageBuilder, groupId);
                results.put(recipient, result);
            }
        }
        return new SendMessageResults(timestamp, results);
    }

    public void sendTypingMessage(
            SignalServiceTypingMessage.Action action, Set<RecipientIdentifier> recipients
    ) throws IOException, UntrustedIdentityException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        final var timestamp = System.currentTimeMillis();
        for (var recipient : recipients) {
            if (recipient instanceof RecipientIdentifier.Single) {
                final var message = new SignalServiceTypingMessage(action, timestamp, Optional.absent());
                final var recipientId = resolveRecipient((RecipientIdentifier.Single) recipient);
                sendHelper.sendTypingMessage(message, recipientId);
            } else if (recipient instanceof RecipientIdentifier.Group) {
                final var groupId = ((RecipientIdentifier.Group) recipient).groupId;
                final var message = new SignalServiceTypingMessage(action, timestamp, Optional.of(groupId.serialize()));
                sendHelper.sendGroupTypingMessage(message, groupId);
            }
        }
    }

    public void sendReadReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    ) throws IOException, UntrustedIdentityException {
        var receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ,
                messageIds,
                System.currentTimeMillis());

        sendHelper.sendReceiptMessage(receiptMessage, resolveRecipient(sender));
    }

    public void sendViewedReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    ) throws IOException, UntrustedIdentityException {
        var receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.VIEWED,
                messageIds,
                System.currentTimeMillis());

        sendHelper.sendReceiptMessage(receiptMessage, resolveRecipient(sender));
    }

    public SendMessageResults sendMessage(
            Message message, Set<RecipientIdentifier> recipients
    ) throws IOException, AttachmentInvalidException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        final var messageBuilder = SignalServiceDataMessage.newBuilder();
        applyMessage(messageBuilder, message);
        return sendMessage(messageBuilder, recipients);
    }

    private void applyMessage(
            final SignalServiceDataMessage.Builder messageBuilder, final Message message
    ) throws AttachmentInvalidException, IOException {
        messageBuilder.withBody(message.getMessageText());
        final var attachments = message.getAttachments();
        if (attachments != null) {
            messageBuilder.withAttachments(attachmentHelper.uploadAttachments(attachments));
        }
    }

    public SendMessageResults sendRemoteDeleteMessage(
            long targetSentTimestamp, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        var delete = new SignalServiceDataMessage.RemoteDelete(targetSentTimestamp);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withRemoteDelete(delete);
        return sendMessage(messageBuilder, recipients);
    }

    public SendMessageResults sendMessageReaction(
            String emoji,
            boolean remove,
            RecipientIdentifier.Single targetAuthor,
            long targetSentTimestamp,
            Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        var targetAuthorRecipientId = resolveRecipient(targetAuthor);
        var reaction = new SignalServiceDataMessage.Reaction(emoji,
                remove,
                resolveSignalServiceAddress(targetAuthorRecipientId),
                targetSentTimestamp);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withReaction(reaction);
        return sendMessage(messageBuilder, recipients);
    }

    public SendMessageResults sendEndSessionMessage(Set<RecipientIdentifier.Single> recipients) throws IOException {
        var messageBuilder = SignalServiceDataMessage.newBuilder().asEndSessionMessage();

        try {
            return sendMessage(messageBuilder,
                    recipients.stream().map(RecipientIdentifier.class::cast).collect(Collectors.toSet()));
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new AssertionError(e);
        } finally {
            for (var recipient : recipients) {
                final var recipientId = resolveRecipient(recipient);
                account.getSessionStore().deleteAllSessions(recipientId);
            }
        }
    }

    public void setContactName(
            RecipientIdentifier.Single recipient, String name
    ) throws NotMasterDeviceException {
        if (!account.isMasterDevice()) {
            throw new NotMasterDeviceException();
        }
        contactHelper.setContactName(resolveRecipient(recipient), name);
    }

    public void setContactBlocked(
            RecipientIdentifier.Single recipient, boolean blocked
    ) throws NotMasterDeviceException, IOException {
        if (!account.isMasterDevice()) {
            throw new NotMasterDeviceException();
        }
        contactHelper.setContactBlocked(resolveRecipient(recipient), blocked);
        // TODO cycle our profile key
        syncHelper.sendBlockedList();
    }

    public void setGroupBlocked(
            final GroupId groupId, final boolean blocked
    ) throws GroupNotFoundException, IOException {
        groupHelper.setGroupBlocked(groupId, blocked);
        // TODO cycle our profile key
        syncHelper.sendBlockedList();
    }

    /**
     * Change the expiration timer for a contact
     */
    public void setExpirationTimer(
            RecipientIdentifier.Single recipient, int messageExpirationTimer
    ) throws IOException {
        var recipientId = resolveRecipient(recipient);
        contactHelper.setExpirationTimer(recipientId, messageExpirationTimer);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().asExpirationUpdate();
        try {
            sendMessage(messageBuilder, Set.of(recipient));
        } catch (NotAGroupMemberException | GroupNotFoundException | GroupSendingNotAllowedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Upload the sticker pack from path.
     *
     * @param path Path can be a path to a manifest.json file or to a zip file that contains a manifest.json file
     * @return if successful, returns the URL to install the sticker pack in the signal app
     */
    public URI uploadStickerPack(File path) throws IOException, StickerPackInvalidException {
        var manifest = StickerUtils.getSignalServiceStickerManifestUpload(path);

        var messageSender = dependencies.getMessageSender();

        var packKey = KeyUtils.createStickerUploadKey();
        var packIdString = messageSender.uploadStickerManifest(manifest, packKey);
        var packId = StickerPackId.deserialize(Hex.fromStringCondensed(packIdString));

        var sticker = new Sticker(packId, packKey);
        account.getStickerStore().updateSticker(sticker);

        try {
            return new URI("https",
                    "signal.art",
                    "/addstickers/",
                    "pack_id="
                            + URLEncoder.encode(Hex.toStringCondensed(packId.serialize()), StandardCharsets.UTF_8)
                            + "&pack_key="
                            + URLEncoder.encode(Hex.toStringCondensed(packKey), StandardCharsets.UTF_8));
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    public void requestAllSyncData() throws IOException {
        syncHelper.requestAllSyncData();
    }

    private byte[] getSenderCertificate() {
        byte[] certificate;
        try {
            if (account.isPhoneNumberShared()) {
                certificate = dependencies.getAccountManager().getSenderCertificate();
            } else {
                certificate = dependencies.getAccountManager().getSenderCertificateForPhoneNumberPrivacy();
            }
        } catch (IOException e) {
            logger.warn("Failed to get sender certificate, ignoring: {}", e.getMessage());
            return null;
        }
        // TODO cache for a day
        return certificate;
    }

    private Set<RecipientId> getRecipientIds(Collection<RecipientIdentifier.Single> recipients) {
        final var signalServiceAddresses = new HashSet<SignalServiceAddress>(recipients.size());
        final var addressesMissingUuid = new HashSet<SignalServiceAddress>();

        for (var number : recipients) {
            final var resolvedAddress = resolveSignalServiceAddress(resolveRecipient(number));
            if (resolvedAddress.getUuid().isPresent()) {
                signalServiceAddresses.add(resolvedAddress);
            } else {
                addressesMissingUuid.add(resolvedAddress);
            }
        }

        final var numbersMissingUuid = addressesMissingUuid.stream()
                .map(a -> a.getNumber().get())
                .collect(Collectors.toSet());
        Map<String, UUID> registeredUsers;
        try {
            registeredUsers = getRegisteredUsers(numbersMissingUuid);
        } catch (IOException e) {
            logger.warn("Failed to resolve uuids from server, ignoring: {}", e.getMessage());
            registeredUsers = Map.of();
        }

        for (var address : addressesMissingUuid) {
            final var number = address.getNumber().get();
            if (registeredUsers.containsKey(number)) {
                final var newAddress = resolveSignalServiceAddress(resolveRecipientTrusted(new SignalServiceAddress(
                        registeredUsers.get(number),
                        number)));
                signalServiceAddresses.add(newAddress);
            } else {
                signalServiceAddresses.add(address);
            }
        }

        return signalServiceAddresses.stream().map(this::resolveRecipient).collect(Collectors.toSet());
    }

    private RecipientId refreshRegisteredUser(RecipientId recipientId) throws IOException {
        final var address = resolveSignalServiceAddress(recipientId);
        if (!address.getNumber().isPresent()) {
            return recipientId;
        }
        final var number = address.getNumber().get();
        final var uuidMap = getRegisteredUsers(Set.of(number));
        return resolveRecipientTrusted(new SignalServiceAddress(uuidMap.getOrDefault(number, null), number));
    }

    private Map<String, UUID> getRegisteredUsers(final Set<String> numbers) throws IOException {
        final Map<String, UUID> registeredUsers;
        try {
            registeredUsers = dependencies.getAccountManager()
                    .getRegisteredUsers(ServiceConfig.getIasKeyStore(),
                            numbers,
                            serviceEnvironmentConfig.getCdsMrenclave());
        } catch (Quote.InvalidQuoteFormatException | UnauthenticatedQuoteException | SignatureException | UnauthenticatedResponseException | InvalidKeyException e) {
            throw new IOException(e);
        }

        // Store numbers as recipients so we have the number/uuid association
        registeredUsers.forEach((number, uuid) -> resolveRecipientTrusted(new SignalServiceAddress(uuid, number)));

        return registeredUsers;
    }

    public void sendTypingMessage(
            TypingAction action, Set<RecipientIdentifier> recipients
    ) throws IOException, UntrustedIdentityException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        sendTypingMessage(action.toSignalService(), recipients);
    }

    private void retryFailedReceivedMessages(ReceiveMessageHandler handler, boolean ignoreAttachments) {
        Set<HandleAction> queuedActions = new HashSet<>();
        for (var cachedMessage : account.getMessageCache().getCachedMessages()) {
            var actions = retryFailedReceivedMessage(handler, ignoreAttachments, cachedMessage);
            if (actions != null) {
                queuedActions.addAll(actions);
            }
        }
        handleQueuedActions(queuedActions);
    }

    private List<HandleAction> retryFailedReceivedMessage(
            final ReceiveMessageHandler handler, final boolean ignoreAttachments, final CachedMessage cachedMessage
    ) {
        var envelope = cachedMessage.loadEnvelope();
        if (envelope == null) {
            return null;
        }
        SignalServiceContent content = null;
        List<HandleAction> actions = null;
        if (!envelope.isReceipt()) {
            try {
                content = dependencies.getCipher().decrypt(envelope);
            } catch (ProtocolUntrustedIdentityException e) {
                if (!envelope.hasSource()) {
                    final var identifier = e.getSender();
                    final var recipientId = resolveRecipient(identifier);
                    try {
                        account.getMessageCache().replaceSender(cachedMessage, recipientId);
                    } catch (IOException ioException) {
                        logger.warn("Failed to move cached message to recipient folder: {}", ioException.getMessage());
                    }
                }
                return null;
            } catch (Exception er) {
                // All other errors are not recoverable, so delete the cached message
                cachedMessage.delete();
                return null;
            }
            actions = incomingMessageHandler.handleMessage(envelope, content, ignoreAttachments);
        }
        handler.handleMessage(envelope, content, null);
        cachedMessage.delete();
        return actions;
    }

    public void receiveMessages(
            long timeout,
            TimeUnit unit,
            boolean returnOnTimeout,
            boolean ignoreAttachments,
            ReceiveMessageHandler handler
    ) throws IOException {
        retryFailedReceivedMessages(handler, ignoreAttachments);

        Set<HandleAction> queuedActions = new HashSet<>();

        final var signalWebSocket = dependencies.getSignalWebSocket();
        signalWebSocket.connect();

        var hasCaughtUpWithOldMessages = false;

        while (!Thread.interrupted()) {
            SignalServiceEnvelope envelope;
            final CachedMessage[] cachedMessage = {null};
            account.setLastReceiveTimestamp(System.currentTimeMillis());
            logger.debug("Checking for new message from server");
            try {
                var result = signalWebSocket.readOrEmpty(unit.toMillis(timeout), envelope1 -> {
                    final var recipientId = envelope1.hasSource()
                            ? resolveRecipient(envelope1.getSourceIdentifier())
                            : null;
                    // store message on disk, before acknowledging receipt to the server
                    cachedMessage[0] = account.getMessageCache().cacheMessage(envelope1, recipientId);
                });
                logger.debug("New message received from server");
                if (result.isPresent()) {
                    envelope = result.get();
                } else {
                    // Received indicator that server queue is empty
                    hasCaughtUpWithOldMessages = true;

                    handleQueuedActions(queuedActions);
                    queuedActions.clear();

                    // Continue to wait another timeout for new messages
                    continue;
                }
            } catch (AssertionError e) {
                if (e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                } else {
                    throw e;
                }
            } catch (WebSocketUnavailableException e) {
                logger.debug("Pipe unexpectedly unavailable, connecting");
                signalWebSocket.connect();
                continue;
            } catch (TimeoutException e) {
                if (returnOnTimeout) return;
                continue;
            }

            final var result = incomingMessageHandler.handleEnvelope(envelope, ignoreAttachments, handler);
            queuedActions.addAll(result.first());
            final var exception = result.second();

            if (hasCaughtUpWithOldMessages) {
                handleQueuedActions(queuedActions);
            }
            if (cachedMessage[0] != null) {
                if (exception instanceof ProtocolUntrustedIdentityException) {
                    final var identifier = ((ProtocolUntrustedIdentityException) exception).getSender();
                    final var recipientId = resolveRecipient(identifier);
                    if (!envelope.hasSource()) {
                        try {
                            cachedMessage[0] = account.getMessageCache().replaceSender(cachedMessage[0], recipientId);
                        } catch (IOException ioException) {
                            logger.warn("Failed to move cached message to recipient folder: {}",
                                    ioException.getMessage());
                        }
                    }
                } else {
                    cachedMessage[0].delete();
                }
            }
        }
        handleQueuedActions(queuedActions);
    }

    private void handleQueuedActions(final Collection<HandleAction> queuedActions) {
        for (var action : queuedActions) {
            try {
                action.execute(context);
            } catch (Throwable e) {
                if (e instanceof AssertionError && e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                logger.warn("Message action failed.", e);
            }
        }
    }

    public boolean isContactBlocked(final RecipientIdentifier.Single recipient) {
        final var recipientId = resolveRecipient(recipient);
        return contactHelper.isContactBlocked(recipientId);
    }

    public File getAttachmentFile(SignalServiceAttachmentRemoteId attachmentId) {
        return attachmentHelper.getAttachmentFile(attachmentId);
    }

    public void sendContacts() throws IOException {
        syncHelper.sendContacts();
    }

    public List<Pair<RecipientId, Contact>> getContacts() {
        return account.getContactStore().getContacts();
    }

    public String getContactOrProfileName(RecipientIdentifier.Single recipientIdentifier) {
        final var recipientId = resolveRecipient(recipientIdentifier);

        final var contact = account.getContactStore().getContact(recipientId);
        if (contact != null && !Util.isEmpty(contact.getName())) {
            return contact.getName();
        }

        final var profile = getRecipientProfile(recipientId);
        if (profile != null) {
            return profile.getDisplayName();
        }

        return null;
    }

    public GroupInfo getGroup(GroupId groupId) {
        return groupHelper.getGroup(groupId);
    }

    public List<IdentityInfo> getIdentities() {
        return account.getIdentityKeyStore().getIdentities();
    }

    public List<IdentityInfo> getIdentities(RecipientIdentifier.Single recipient) {
        final var identity = account.getIdentityKeyStore().getIdentity(resolveRecipient(recipient));
        return identity == null ? List.of() : List.of(identity);
    }

    /**
     * Trust this the identity with this fingerprint
     *
     * @param recipient   username of the identity
     * @param fingerprint Fingerprint
     */
    public boolean trustIdentityVerified(RecipientIdentifier.Single recipient, byte[] fingerprint) {
        var recipientId = resolveRecipient(recipient);
        return trustIdentity(recipientId,
                identityKey -> Arrays.equals(identityKey.serialize(), fingerprint),
                TrustLevel.TRUSTED_VERIFIED);
    }

    /**
     * Trust this the identity with this safety number
     *
     * @param recipient    username of the identity
     * @param safetyNumber Safety number
     */
    public boolean trustIdentityVerifiedSafetyNumber(RecipientIdentifier.Single recipient, String safetyNumber) {
        var recipientId = resolveRecipient(recipient);
        var address = account.getRecipientStore().resolveServiceAddress(recipientId);
        return trustIdentity(recipientId,
                identityKey -> safetyNumber.equals(computeSafetyNumber(address, identityKey)),
                TrustLevel.TRUSTED_VERIFIED);
    }

    /**
     * Trust this the identity with this scannable safety number
     *
     * @param recipient    username of the identity
     * @param safetyNumber Scannable safety number
     */
    public boolean trustIdentityVerifiedSafetyNumber(RecipientIdentifier.Single recipient, byte[] safetyNumber) {
        var recipientId = resolveRecipient(recipient);
        var address = account.getRecipientStore().resolveServiceAddress(recipientId);
        return trustIdentity(recipientId, identityKey -> {
            final var fingerprint = computeSafetyNumberFingerprint(address, identityKey);
            try {
                return fingerprint != null && fingerprint.getScannableFingerprint().compareTo(safetyNumber);
            } catch (FingerprintVersionMismatchException | FingerprintParsingException e) {
                return false;
            }
        }, TrustLevel.TRUSTED_VERIFIED);
    }

    /**
     * Trust all keys of this identity without verification
     *
     * @param recipient username of the identity
     */
    public boolean trustIdentityAllKeys(RecipientIdentifier.Single recipient) {
        var recipientId = resolveRecipient(recipient);
        return trustIdentity(recipientId, identityKey -> true, TrustLevel.TRUSTED_UNVERIFIED);
    }

    private boolean trustIdentity(
            RecipientId recipientId, Function<IdentityKey, Boolean> verifier, TrustLevel trustLevel
    ) {
        var identity = account.getIdentityKeyStore().getIdentity(recipientId);
        if (identity == null) {
            return false;
        }

        if (!verifier.apply(identity.getIdentityKey())) {
            return false;
        }

        account.getIdentityKeyStore().setIdentityTrustLevel(recipientId, identity.getIdentityKey(), trustLevel);
        try {
            var address = account.getRecipientStore().resolveServiceAddress(recipientId);
            syncHelper.sendVerifiedMessage(address, identity.getIdentityKey(), trustLevel);
        } catch (IOException e) {
            logger.warn("Failed to send verification sync message: {}", e.getMessage());
        }

        return true;
    }

    private void handleIdentityFailure(
            final RecipientId recipientId, final SendMessageResult.IdentityFailure identityFailure
    ) {
        final var identityKey = identityFailure.getIdentityKey();
        if (identityKey != null) {
            final var newIdentity = account.getIdentityKeyStore().saveIdentity(recipientId, identityKey, new Date());
            if (newIdentity) {
                account.getSessionStore().archiveSessions(recipientId);
            }
        } else {
            // Retrieve profile to get the current identity key from the server
            profileHelper.refreshRecipientProfile(recipientId);
        }
    }

    public String computeSafetyNumber(SignalServiceAddress theirAddress, IdentityKey theirIdentityKey) {
        final Fingerprint fingerprint = computeSafetyNumberFingerprint(theirAddress, theirIdentityKey);
        return fingerprint == null ? null : fingerprint.getDisplayableFingerprint().getDisplayText();
    }

    public byte[] computeSafetyNumberForScanning(SignalServiceAddress theirAddress, IdentityKey theirIdentityKey) {
        final Fingerprint fingerprint = computeSafetyNumberFingerprint(theirAddress, theirIdentityKey);
        return fingerprint == null ? null : fingerprint.getScannableFingerprint().getSerialized();
    }

    private Fingerprint computeSafetyNumberFingerprint(
            final SignalServiceAddress theirAddress, final IdentityKey theirIdentityKey
    ) {
        return Utils.computeSafetyNumber(capabilities.isUuid(),
                account.getSelfAddress(),
                getIdentityKeyPair().getPublicKey(),
                theirAddress,
                theirIdentityKey);
    }

    @Deprecated
    public SignalServiceAddress resolveSignalServiceAddress(String identifier) {
        var address = Utils.getSignalServiceAddressFromIdentifier(identifier);

        return resolveSignalServiceAddress(address);
    }

    @Deprecated
    public SignalServiceAddress resolveSignalServiceAddress(SignalServiceAddress address) {
        if (address.matches(account.getSelfAddress())) {
            return account.getSelfAddress();
        }

        return account.getRecipientStore().resolveServiceAddress(address);
    }

    public SignalServiceAddress resolveSignalServiceAddress(RecipientId recipientId) {
        return account.getRecipientStore().resolveServiceAddress(recipientId);
    }

    private String canonicalizePhoneNumber(final String number) throws InvalidNumberException {
        return PhoneNumberFormatter.formatNumber(number, account.getUsername());
    }

    private RecipientId resolveRecipient(final String identifier) {
        var address = Utils.getSignalServiceAddressFromIdentifier(identifier);

        return resolveRecipient(address);
    }

    private RecipientId resolveRecipient(final RecipientIdentifier.Single recipient) {
        final SignalServiceAddress address;
        if (recipient instanceof RecipientIdentifier.Uuid) {
            address = new SignalServiceAddress(((RecipientIdentifier.Uuid) recipient).uuid, null);
        } else {
            address = new SignalServiceAddress(null, ((RecipientIdentifier.Number) recipient).number);
        }

        return resolveRecipient(address);
    }

    public RecipientId resolveRecipient(SignalServiceAddress address) {
        return account.getRecipientStore().resolveRecipient(address);
    }

    private RecipientId resolveRecipientTrusted(SignalServiceAddress address) {
        return account.getRecipientStore().resolveRecipientTrusted(address);
    }

    @Override
    public void close() throws IOException {
        close(true);
    }

    private void close(boolean closeAccount) throws IOException {
        executor.shutdown();

        dependencies.getSignalWebSocket().disconnect();

        if (closeAccount && account != null) {
            account.close();
        }
        account = null;
    }

    public interface ReceiveMessageHandler {

        void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e);
    }
}
