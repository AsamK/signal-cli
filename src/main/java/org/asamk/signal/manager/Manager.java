/*
  Copyright (C) 2015-2020 AsamK and contributors

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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.storage.SignalAccount;
import org.asamk.signal.storage.contacts.ContactInfo;
import org.asamk.signal.storage.groups.GroupInfo;
import org.asamk.signal.storage.groups.GroupInfoV1;
import org.asamk.signal.storage.groups.GroupInfoV2;
import org.asamk.signal.storage.profiles.SignalProfile;
import org.asamk.signal.storage.profiles.SignalProfileEntry;
import org.asamk.signal.storage.protocol.JsonIdentityKeyStore;
import org.asamk.signal.storage.stickers.Sticker;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.Util;
import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.InvalidMetadataVersionException;
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidVersionException;
import org.signal.libsignal.metadata.ProtocolLegacyMessageException;
import org.signal.libsignal.metadata.ProtocolNoSessionException;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.signal.libsignal.metadata.SelfSendException;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStickerManifestUpload;
import org.whispersystems.signalservice.api.messages.SignalServiceStickerManifestUpload.StickerInfo;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.util.Base64;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.asamk.signal.manager.ServiceConfig.capabilities;

public class Manager implements Closeable {

    private final SleepTimer timer = new UptimeSleepTimer();
    private final SignalServiceConfiguration serviceConfiguration;
    private final String userAgent;

    private final SignalAccount account;
    private final PathConfig pathConfig;
    private SignalServiceAccountManager accountManager;
    private GroupsV2Api groupsV2Api;
    private SignalServiceMessagePipe messagePipe = null;
    private SignalServiceMessagePipe unidentifiedMessagePipe = null;
    private final boolean discoverableByPhoneNumber = true;

    public Manager(SignalAccount account, PathConfig pathConfig, SignalServiceConfiguration serviceConfiguration, String userAgent) {
        this.account = account;
        this.pathConfig = pathConfig;
        this.serviceConfiguration = serviceConfiguration;
        this.userAgent = userAgent;
        this.accountManager = createSignalServiceAccountManager();
        this.groupsV2Api = accountManager.getGroupsV2Api();

        this.account.setResolver(this::resolveSignalServiceAddress);
    }

    public String getUsername() {
        return account.getUsername();
    }

    public SignalServiceAddress getSelfAddress() {
        return account.getSelfAddress();
    }

    private SignalServiceAccountManager createSignalServiceAccountManager() {
        GroupsV2Operations groupsV2Operations = capabilities.isGv2()
                ? new GroupsV2Operations(ClientZkOperations.create(serviceConfiguration))
                : null;

        return new SignalServiceAccountManager(serviceConfiguration,
                new DynamicCredentialsProvider(account.getUuid(), account.getUsername(), account.getPassword(), null, account.getDeviceId()),
                userAgent,
                groupsV2Operations,
                timer);
    }

    private IdentityKeyPair getIdentityKeyPair() {
        return account.getSignalProtocolStore().getIdentityKeyPair();
    }

    public int getDeviceId() {
        return account.getDeviceId();
    }

    private String getMessageCachePath() {
        return pathConfig.getDataPath() + "/" + account.getUsername() + ".d/msg-cache";
    }

    private String getMessageCachePath(String sender) {
        if (sender == null || sender.isEmpty()) {
            return getMessageCachePath();
        }

        return getMessageCachePath() + "/" + sender.replace("/", "_");
    }

    private File getMessageCacheFile(String sender, long now, long timestamp) throws IOException {
        String cachePath = getMessageCachePath(sender);
        IOUtils.createPrivateDirectories(cachePath);
        return new File(cachePath + "/" + now + "_" + timestamp);
    }

    public static Manager init(String username, String settingsPath, SignalServiceConfiguration serviceConfiguration, String userAgent) throws IOException {
        PathConfig pathConfig = PathConfig.createDefault(settingsPath);

        if (!SignalAccount.userExists(pathConfig.getDataPath(), username)) {
            IdentityKeyPair identityKey = KeyHelper.generateIdentityKeyPair();
            int registrationId = KeyHelper.generateRegistrationId(false);

            ProfileKey profileKey = KeyUtils.createProfileKey();
            SignalAccount account = SignalAccount.create(pathConfig.getDataPath(), username, identityKey, registrationId, profileKey);
            account.save();

            return new Manager(account, pathConfig, serviceConfiguration, userAgent);
        }

        SignalAccount account = SignalAccount.load(pathConfig.getDataPath(), username);

        Manager m = new Manager(account, pathConfig, serviceConfiguration, userAgent);

        m.migrateLegacyConfigs();
        m.updateAccountAttributes();

        return m;
    }

    private void migrateLegacyConfigs() {
        if (account.getProfileKey() == null) {
            // Old config file, creating new profile key
            account.setProfileKey(KeyUtils.createProfileKey());
            account.save();
        }
        // Store profile keys only in profile store
        for (ContactInfo contact : account.getContactStore().getContacts()) {
            String profileKeyString = contact.profileKey;
            if (profileKeyString == null) {
                continue;
            }
            final ProfileKey profileKey;
            try {
                profileKey = new ProfileKey(Base64.decode(profileKeyString));
            } catch (InvalidInputException | IOException e) {
                continue;
            }
            contact.profileKey = null;
            account.getProfileStore().storeProfileKey(contact.getAddress(), profileKey);
        }
    }

    public void checkAccountState() throws IOException {
        if (account.isRegistered()) {
            if (accountManager.getPreKeysCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
                refreshPreKeys();
                account.save();
            }
            if (account.getUuid() == null) {
                account.setUuid(accountManager.getOwnUuid());
                account.save();
            }
        }
    }

    public boolean isRegistered() {
        return account.isRegistered();
    }

    public void register(boolean voiceVerification, String captcha) throws IOException {
        account.setPassword(KeyUtils.createPassword());

        // Resetting UUID, because registering doesn't work otherwise
        account.setUuid(null);
        accountManager = createSignalServiceAccountManager();
        this.groupsV2Api = accountManager.getGroupsV2Api();

        if (voiceVerification) {
            accountManager.requestVoiceVerificationCode(Locale.getDefault(), Optional.fromNullable(captcha), Optional.absent());
        } else {
            accountManager.requestSmsVerificationCode(false, Optional.fromNullable(captcha), Optional.absent());
        }

        account.setRegistered(false);
        account.save();
    }

    public void updateAccountAttributes() throws IOException {
        accountManager.setAccountAttributes(account.getSignalingKey(), account.getSignalProtocolStore().getLocalRegistrationId(), true, account.getRegistrationLockPin(), account.getRegistrationLock(), getSelfUnidentifiedAccessKey(), false, capabilities, discoverableByPhoneNumber);
    }

    public void setProfile(String name, File avatar) throws IOException {
        try (final StreamDetails streamDetails = avatar == null ? null : Utils.createStreamDetailsFromFile(avatar)) {
            accountManager.setVersionedProfile(account.getUuid(), account.getProfileKey(), name, streamDetails);
        }
    }

    public void unregister() throws IOException {
        // When setting an empty GCM id, the Signal-Server also sets the fetchesMessages property to false.
        // If this is the master device, other users can't send messages to this number anymore.
        // If this is a linked device, other users can still send messages, but this device doesn't receive them anymore.
        accountManager.setGcmId(Optional.absent());

        account.setRegistered(false);
        account.save();
    }

    public List<DeviceInfo> getLinkedDevices() throws IOException {
        List<DeviceInfo> devices = accountManager.getDevices();
        account.setMultiDevice(devices.size() > 1);
        account.save();
        return devices;
    }

    public void removeLinkedDevices(int deviceId) throws IOException {
        accountManager.removeDevice(deviceId);
        List<DeviceInfo> devices = accountManager.getDevices();
        account.setMultiDevice(devices.size() > 1);
        account.save();
    }

    public void addDeviceLink(URI linkUri) throws IOException, InvalidKeyException {
        Utils.DeviceLinkInfo info = Utils.parseDeviceLinkUri(linkUri);

        addDevice(info.deviceIdentifier, info.deviceKey);
    }

    private void addDevice(String deviceIdentifier, ECPublicKey deviceKey) throws IOException, InvalidKeyException {
        IdentityKeyPair identityKeyPair = getIdentityKeyPair();
        String verificationCode = accountManager.getNewDeviceVerificationCode();

        accountManager.addDevice(deviceIdentifier, deviceKey, identityKeyPair, Optional.of(account.getProfileKey().serialize()), verificationCode);
        account.setMultiDevice(true);
        account.save();
    }

    private List<PreKeyRecord> generatePreKeys() {
        List<PreKeyRecord> records = new ArrayList<>(ServiceConfig.PREKEY_BATCH_SIZE);

        final int offset = account.getPreKeyIdOffset();
        for (int i = 0; i < ServiceConfig.PREKEY_BATCH_SIZE; i++) {
            int preKeyId = (offset + i) % Medium.MAX_VALUE;
            ECKeyPair keyPair = Curve.generateKeyPair();
            PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

            records.add(record);
        }

        account.addPreKeys(records);
        account.save();

        return records;
    }

    private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair) {
        try {
            ECKeyPair keyPair = Curve.generateKeyPair();
            byte[] signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
            SignedPreKeyRecord record = new SignedPreKeyRecord(account.getNextSignedPreKeyId(), System.currentTimeMillis(), keyPair, signature);

            account.addSignedPreKey(record);
            account.save();

            return record;
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public void verifyAccount(String verificationCode, String pin) throws IOException {
        verificationCode = verificationCode.replace("-", "");
        account.setSignalingKey(KeyUtils.createSignalingKey());
        // TODO make unrestricted unidentified access configurable
        VerifyAccountResponse response = accountManager.verifyAccountWithCode(verificationCode, account.getSignalingKey(), account.getSignalProtocolStore().getLocalRegistrationId(), true, pin, null, getSelfUnidentifiedAccessKey(), false, capabilities, discoverableByPhoneNumber);

        UUID uuid = UuidUtil.parseOrNull(response.getUuid());
        // TODO response.isStorageCapable()
        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
        account.setRegistered(true);
        account.setUuid(uuid);
        account.setRegistrationLockPin(pin);
        account.getSignalProtocolStore().saveIdentity(account.getSelfAddress(), getIdentityKeyPair().getPublicKey(), TrustLevel.TRUSTED_VERIFIED);

        refreshPreKeys();
        account.save();
    }

    public void setRegistrationLockPin(Optional<String> pin) throws IOException {
        if (pin.isPresent()) {
            account.setRegistrationLockPin(pin.get());
            throw new RuntimeException("Not implemented anymore, will be replaced with KBS");
        } else {
            account.setRegistrationLockPin(null);
            accountManager.removeRegistrationLockV1();
        }
        account.save();
    }

    void refreshPreKeys() throws IOException {
        List<PreKeyRecord> oneTimePreKeys = generatePreKeys();
        final IdentityKeyPair identityKeyPair = getIdentityKeyPair();
        SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey(identityKeyPair);

        accountManager.setPreKeys(identityKeyPair.getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
    }

    private SignalServiceMessageReceiver getMessageReceiver() {
        final ClientZkProfileOperations clientZkProfileOperations = capabilities.isGv2()
                ? ClientZkOperations.create(serviceConfiguration).getProfileOperations()
                : null;
        return new SignalServiceMessageReceiver(serviceConfiguration, account.getUuid(), account.getUsername(), account.getPassword(), account.getDeviceId(), account.getSignalingKey(), userAgent, null, timer, clientZkProfileOperations);
    }

    private SignalServiceMessageSender getMessageSender() {
        final ClientZkProfileOperations clientZkProfileOperations = capabilities.isGv2()
                ? ClientZkOperations.create(serviceConfiguration).getProfileOperations()
                : null;
        final ExecutorService executor = null;
        return new SignalServiceMessageSender(serviceConfiguration, account.getUuid(), account.getUsername(), account.getPassword(),
                account.getDeviceId(), account.getSignalProtocolStore(), userAgent, account.isMultiDevice(), Optional.fromNullable(messagePipe), Optional.fromNullable(unidentifiedMessagePipe), Optional.absent(), clientZkProfileOperations, executor, ServiceConfig.MAX_ENVELOPE_SIZE);
    }

    private SignalServiceProfile getEncryptedRecipientProfile(SignalServiceAddress address, Optional<UnidentifiedAccess> unidentifiedAccess) throws IOException {
        SignalServiceMessagePipe pipe = unidentifiedMessagePipe != null && unidentifiedAccess.isPresent() ? unidentifiedMessagePipe
                : messagePipe;

        if (pipe != null) {
            try {
                return pipe.getProfile(address, Optional.absent(), unidentifiedAccess, SignalServiceProfile.RequestType.PROFILE).get(10, TimeUnit.SECONDS).getProfile();
            } catch (IOException | InterruptedException | ExecutionException | TimeoutException ignored) {
            }
        }

        SignalServiceMessageReceiver receiver = getMessageReceiver();
        try {
            return receiver.retrieveProfile(address, Optional.absent(), unidentifiedAccess, SignalServiceProfile.RequestType.PROFILE).get(10, TimeUnit.SECONDS).getProfile();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException("Failed to retrieve profile", e);
        }
    }

    private SignalProfile getRecipientProfile(SignalServiceAddress address, Optional<UnidentifiedAccess> unidentifiedAccess, ProfileKey profileKey) throws IOException {
        SignalProfileEntry profileEntry = account.getProfileStore().getProfile(address);
        long now = new Date().getTime();
        // Profiles are cache for 24h before retrieving them again
        if (profileEntry == null || profileEntry.getProfile() == null || now - profileEntry.getLastUpdateTimestamp() > 24 * 60 * 60 * 1000) {
            SignalProfile profile = retrieveRecipientProfile(address, unidentifiedAccess, profileKey);
            account.getProfileStore().updateProfile(address, profileKey, now, profile);
            return profile;
        }
        return profileEntry.getProfile();
    }

    private SignalProfile retrieveRecipientProfile(SignalServiceAddress address, Optional<UnidentifiedAccess> unidentifiedAccess, ProfileKey profileKey) throws IOException {
        final SignalServiceProfile encryptedProfile = getEncryptedRecipientProfile(address, unidentifiedAccess);

        File avatarFile = null;
        try {
            avatarFile = encryptedProfile.getAvatar() == null ? null : retrieveProfileAvatar(address, encryptedProfile.getAvatar(), profileKey);
        } catch (Throwable e) {
            System.err.println("Failed to retrieve profile avatar, ignoring: " + e.getMessage());
        }

        ProfileCipher profileCipher = new ProfileCipher(profileKey);
        try {
            return new SignalProfile(
                    encryptedProfile.getIdentityKey(),
                    encryptedProfile.getName() == null ? null : new String(profileCipher.decryptName(Base64.decode(encryptedProfile.getName()))),
                    avatarFile,
                    encryptedProfile.getUnidentifiedAccess() == null || !profileCipher.verifyUnidentifiedAccess(Base64.decode(encryptedProfile.getUnidentifiedAccess())) ? null : encryptedProfile.getUnidentifiedAccess(),
                    encryptedProfile.isUnrestrictedUnidentifiedAccess(),
                    encryptedProfile.getCapabilities());
        } catch (InvalidCiphertextException e) {
            return null;
        }
    }

    private Optional<SignalServiceAttachmentStream> createGroupAvatarAttachment(byte[] groupId) throws IOException {
        File file = getGroupAvatarFile(groupId);
        if (!file.exists()) {
            return Optional.absent();
        }

        return Optional.of(Utils.createAttachment(file));
    }

    private Optional<SignalServiceAttachmentStream> createContactAvatarAttachment(String number) throws IOException {
        File file = getContactAvatarFile(number);
        if (!file.exists()) {
            return Optional.absent();
        }

        return Optional.of(Utils.createAttachment(file));
    }

    private GroupInfo getGroupForSending(byte[] groupId) throws GroupNotFoundException, NotAGroupMemberException {
        GroupInfo g = account.getGroupStore().getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        if (!g.isMember(account.getSelfAddress())) {
            throw new NotAGroupMemberException(groupId, g.getTitle());
        }
        return g;
    }

    public List<GroupInfo> getGroups() {
        return account.getGroupStore().getGroups();
    }

    public Pair<Long, List<SendMessageResult>> sendGroupMessage(
            String messageText,
            List<String> attachments,
            byte[] groupId
    )
            throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(Utils.getSignalServiceAttachments(attachments));
        }

        final GroupInfo g = getGroupForSending(groupId);

        setGroupContext(messageBuilder, g);
        messageBuilder.withExpiration(g.getMessageExpirationTime());

        return sendMessage(messageBuilder, g.getMembersWithout(account.getSelfAddress()));
    }

    private void setGroupContext(final SignalServiceDataMessage.Builder messageBuilder, final GroupInfo groupInfo) {
        if (groupInfo instanceof GroupInfoV1) {
            SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.DELIVER)
                    .withId(groupInfo.groupId)
                    .build();
            messageBuilder.asGroupMessage(group);
        } else {
            final GroupInfoV2 groupInfoV2 = (GroupInfoV2) groupInfo;
            SignalServiceGroupV2 group = SignalServiceGroupV2.newBuilder(groupInfoV2.getMasterKey())
                    .withRevision(groupInfoV2.getGroup() == null ? 0 : groupInfoV2.getGroup().getRevision())
                    .build();
            messageBuilder.asGroupMessage(group);
        }
    }

    public Pair<Long, List<SendMessageResult>> sendGroupMessageReaction(String emoji, boolean remove, String targetAuthor,
                                                                        long targetSentTimestamp, byte[] groupId)
            throws IOException, InvalidNumberException, NotAGroupMemberException, GroupNotFoundException {
        SignalServiceDataMessage.Reaction reaction = new SignalServiceDataMessage.Reaction(emoji, remove, canonicalizeAndResolveSignalServiceAddress(targetAuthor), targetSentTimestamp);
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .withReaction(reaction);
        final GroupInfo g = getGroupForSending(groupId);
        setGroupContext(messageBuilder, g);
        return sendMessage(messageBuilder, g.getMembersWithout(account.getSelfAddress()));
    }

    public Pair<Long, List<SendMessageResult>> sendQuitGroupMessage(byte[] groupId) throws GroupNotFoundException, IOException, NotAGroupMemberException {
        SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.QUIT)
                .withId(groupId)
                .build();

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group);

        final GroupInfo g = getGroupForSending(groupId);
        if (g instanceof GroupInfoV1) {
            GroupInfoV1 groupInfoV1 = (GroupInfoV1) g;
            groupInfoV1.removeMember(account.getSelfAddress());
            account.getGroupStore().updateGroup(groupInfoV1);
        } else {
            throw new RuntimeException("TODO Not implemented!");
        }

        return sendMessage(messageBuilder, g.getMembersWithout(account.getSelfAddress()));
    }

    private Pair<byte[], List<SendMessageResult>> sendUpdateGroupMessage(byte[] groupId, String name, Collection<SignalServiceAddress> members, String avatarFile) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException {
        GroupInfoV1 g;
        if (groupId == null) {
            // Create new group
            g = new GroupInfoV1(KeyUtils.createGroupId());
            g.addMembers(Collections.singleton(account.getSelfAddress()));
        } else {
            GroupInfo group = getGroupForSending(groupId);
            if (!(group instanceof GroupInfoV1)) {
                throw new RuntimeException("TODO Not implemented!");
            }
            g = (GroupInfoV1) group;
        }

        if (name != null) {
            g.name = name;
        }

        if (members != null) {
            final Set<String> newE164Members = new HashSet<>();
            for (SignalServiceAddress member : members) {
                if (g.isMember(member) || !member.getNumber().isPresent()) {
                    continue;
                }
                newE164Members.add(member.getNumber().get());
            }

            final List<ContactTokenDetails> contacts = accountManager.getContacts(newE164Members);
            if (contacts.size() != newE164Members.size()) {
                // Some of the new members are not registered on Signal
                for (ContactTokenDetails contact : contacts) {
                    newE164Members.remove(contact.getNumber());
                }
                throw new IOException("Failed to add members " + Util.join(", ", newE164Members) + " to group: Not registered on Signal");
            }

            g.addMembers(members);
        }

        if (avatarFile != null) {
            IOUtils.createPrivateDirectories(pathConfig.getAvatarsPath());
            File aFile = getGroupAvatarFile(g.groupId);
            Files.copy(Paths.get(avatarFile), aFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        account.getGroupStore().updateGroup(g);

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        final Pair<Long, List<SendMessageResult>> result = sendMessage(messageBuilder, g.getMembersWithout(account.getSelfAddress()));
        return new Pair<>(g.groupId, result.second());
    }

    Pair<Long, List<SendMessageResult>> sendUpdateGroupMessage(byte[] groupId, SignalServiceAddress recipient) throws IOException, NotAGroupMemberException, GroupNotFoundException, AttachmentInvalidException {
        GroupInfoV1 g;
        GroupInfo group = getGroupForSending(groupId);
        if (!(group instanceof GroupInfoV1)) {
            throw new RuntimeException("TODO Not implemented!");
        }
        g = (GroupInfoV1) group;

        if (!g.isMember(recipient)) {
            throw new NotAGroupMemberException(groupId, g.name);
        }

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        // Send group message only to the recipient who requested it
        return sendMessage(messageBuilder, Collections.singleton(recipient));
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfoV1 g) throws AttachmentInvalidException {
        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                .withId(g.groupId)
                .withName(g.name)
                .withMembers(new ArrayList<>(g.getMembers()));

        File aFile = getGroupAvatarFile(g.groupId);
        if (aFile.exists()) {
            try {
                group.withAvatar(Utils.createAttachment(aFile));
            } catch (IOException e) {
                throw new AttachmentInvalidException(aFile.toString(), e);
            }
        }

        return SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build())
                .withExpiration(g.messageExpirationTime);
    }

    Pair<Long, List<SendMessageResult>> sendGroupInfoRequest(byte[] groupId, SignalServiceAddress recipient) throws IOException {
        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.REQUEST_INFO)
                .withId(groupId);

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build());

        // Send group info request message to the recipient who sent us a message with this groupId
        return sendMessage(messageBuilder, Collections.singleton(recipient));
    }

    void sendReceipt(SignalServiceAddress remoteAddress, long messageId) throws IOException, UntrustedIdentityException {
        SignalServiceReceiptMessage receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.DELIVERY,
                Collections.singletonList(messageId),
                System.currentTimeMillis());

        getMessageSender().sendReceipt(remoteAddress, getAccessFor(remoteAddress), receiptMessage);
    }

    public Pair<Long, List<SendMessageResult>> sendMessage(String messageText, List<String> attachments,
                                                           List<String> recipients)
            throws IOException, AttachmentInvalidException, InvalidNumberException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            List<SignalServiceAttachment> attachmentStreams = Utils.getSignalServiceAttachments(attachments);

            // Upload attachments here, so we only upload once even for multiple recipients
            SignalServiceMessageSender messageSender = getMessageSender();
            List<SignalServiceAttachment> attachmentPointers = new ArrayList<>(attachmentStreams.size());
            for (SignalServiceAttachment attachment : attachmentStreams) {
                if (attachment.isStream()) {
                    attachmentPointers.add(messageSender.uploadAttachment(attachment.asStream()));
                } else if (attachment.isPointer()) {
                    attachmentPointers.add(attachment.asPointer());
                }
            }

            messageBuilder.withAttachments(attachmentPointers);
        }
        return sendMessage(messageBuilder, getSignalServiceAddresses(recipients));
    }

    public Pair<Long, List<SendMessageResult>> sendMessageReaction(String emoji, boolean remove, String targetAuthor,
                                                                   long targetSentTimestamp, List<String> recipients)
            throws IOException, InvalidNumberException {
        SignalServiceDataMessage.Reaction reaction = new SignalServiceDataMessage.Reaction(emoji, remove, canonicalizeAndResolveSignalServiceAddress(targetAuthor), targetSentTimestamp);
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .withReaction(reaction);
        return sendMessage(messageBuilder, getSignalServiceAddresses(recipients));
    }

    public Pair<Long, List<SendMessageResult>> sendEndSessionMessage(List<String> recipients) throws IOException, InvalidNumberException {
        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asEndSessionMessage();

        final Collection<SignalServiceAddress> signalServiceAddresses = getSignalServiceAddresses(recipients);
        try {
            return sendMessage(messageBuilder, signalServiceAddresses);
        } catch (Exception e) {
            for (SignalServiceAddress address : signalServiceAddresses) {
                handleEndSession(address);
            }
            account.save();
            throw e;
        }
    }

    public String getContactName(String number) throws InvalidNumberException {
        ContactInfo contact = account.getContactStore().getContact(canonicalizeAndResolveSignalServiceAddress(number));
        if (contact == null) {
            return "";
        } else {
            return contact.name;
        }
    }

    public void setContactName(String number, String name) throws InvalidNumberException {
        final SignalServiceAddress address = canonicalizeAndResolveSignalServiceAddress(number);
        ContactInfo contact = account.getContactStore().getContact(address);
        if (contact == null) {
            contact = new ContactInfo(address);
        }
        contact.name = name;
        account.getContactStore().updateContact(contact);
        account.save();
    }

    public void setContactBlocked(String number, boolean blocked) throws InvalidNumberException {
        setContactBlocked(canonicalizeAndResolveSignalServiceAddress(number), blocked);
    }

    private void setContactBlocked(SignalServiceAddress address, boolean blocked) {
        ContactInfo contact = account.getContactStore().getContact(address);
        if (contact == null) {
            contact = new ContactInfo(address);
        }
        contact.blocked = blocked;
        account.getContactStore().updateContact(contact);
        account.save();
    }

    public void setGroupBlocked(final byte[] groupId, final boolean blocked) throws GroupNotFoundException {
        GroupInfo group = getGroup(groupId);
        if (group == null) {
            throw new GroupNotFoundException(groupId);
        }

        group.setBlocked(blocked);
        account.getGroupStore().updateGroup(group);
        account.save();
    }

    public Pair<byte[], List<SendMessageResult>> updateGroup(byte[] groupId, String name, List<String> members, String avatar) throws IOException, GroupNotFoundException, AttachmentInvalidException, InvalidNumberException, NotAGroupMemberException {
        if (groupId.length == 0) {
            groupId = null;
        }
        if (name.isEmpty()) {
            name = null;
        }
        if (members.isEmpty()) {
            members = null;
        }
        if (avatar.isEmpty()) {
            avatar = null;
        }
        return sendUpdateGroupMessage(groupId, name, members == null ? null : getSignalServiceAddresses(members), avatar);
    }

    /**
     * Change the expiration timer for a contact
     */
    public void setExpirationTimer(SignalServiceAddress address, int messageExpirationTimer) throws IOException {
        ContactInfo contact = account.getContactStore().getContact(address);
        contact.messageExpirationTime = messageExpirationTimer;
        account.getContactStore().updateContact(contact);
        sendExpirationTimerUpdate(address);
        account.save();
    }

    private void sendExpirationTimerUpdate(SignalServiceAddress address) throws IOException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asExpirationUpdate();
        sendMessage(messageBuilder, Collections.singleton(address));
    }

    /**
     * Change the expiration timer for a contact
     */
    public void setExpirationTimer(String number, int messageExpirationTimer) throws IOException, InvalidNumberException {
        SignalServiceAddress address = canonicalizeAndResolveSignalServiceAddress(number);
        setExpirationTimer(address, messageExpirationTimer);
    }

    /**
     * Change the expiration timer for a group
     */
    public void setExpirationTimer(byte[] groupId, int messageExpirationTimer) {
        GroupInfo g = account.getGroupStore().getGroup(groupId);
        if (g instanceof GroupInfoV1) {
            GroupInfoV1 groupInfoV1 = (GroupInfoV1) g;
            groupInfoV1.messageExpirationTime = messageExpirationTimer;
            account.getGroupStore().updateGroup(groupInfoV1);
        } else {
            throw new RuntimeException("TODO Not implemented!");
        }
    }

    /**
     * Upload the sticker pack from path.
     *
     * @param path Path can be a path to a manifest.json file or to a zip file that contains a manifest.json file
     * @return if successful, returns the URL to install the sticker pack in the signal app
     */
    public String uploadStickerPack(String path) throws IOException, StickerPackInvalidException {
        SignalServiceStickerManifestUpload manifest = getSignalServiceStickerManifestUpload(path);

        SignalServiceMessageSender messageSender = getMessageSender();

        byte[] packKey = KeyUtils.createStickerUploadKey();
        String packId = messageSender.uploadStickerManifest(manifest, packKey);

        Sticker sticker = new Sticker(Hex.fromStringCondensed(packId), packKey);
        account.getStickerStore().updateSticker(sticker);
        account.save();

        try {
            return new URI("https", "signal.art", "/addstickers/", "pack_id=" + URLEncoder.encode(packId, StandardCharsets.UTF_8) + "&pack_key=" + URLEncoder.encode(Hex.toStringCondensed(packKey), StandardCharsets.UTF_8))
                    .toString();
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private SignalServiceStickerManifestUpload getSignalServiceStickerManifestUpload(final String path) throws IOException, StickerPackInvalidException {
        ZipFile zip = null;
        String rootPath = null;

        final File file = new File(path);
        if (file.getName().endsWith(".zip")) {
            zip = new ZipFile(file);
        } else if (file.getName().equals("manifest.json")) {
            rootPath = file.getParent();
        } else {
            throw new StickerPackInvalidException("Could not find manifest.json");
        }

        JsonStickerPack pack = parseStickerPack(rootPath, zip);

        if (pack.stickers == null) {
            throw new StickerPackInvalidException("Must set a 'stickers' field.");
        }

        if (pack.stickers.isEmpty()) {
            throw new StickerPackInvalidException("Must include stickers.");
        }

        List<StickerInfo> stickers = new ArrayList<>(pack.stickers.size());
        for (JsonStickerPack.JsonSticker sticker : pack.stickers) {
            if (sticker.file == null) {
                throw new StickerPackInvalidException("Must set a 'file' field on each sticker.");
            }

            Pair<InputStream, Long> data;
            try {
                data = getInputStreamAndLength(rootPath, zip, sticker.file);
            } catch (IOException ignored) {
                throw new StickerPackInvalidException("Could not find find " + sticker.file);
            }

            String contentType = Utils.getFileMimeType(new File(sticker.file), null);
            StickerInfo stickerInfo = new StickerInfo(data.first(), data.second(), Optional.fromNullable(sticker.emoji).or(""), contentType);
            stickers.add(stickerInfo);
        }

        StickerInfo cover = null;
        if (pack.cover != null) {
            if (pack.cover.file == null) {
                throw new StickerPackInvalidException("Must set a 'file' field on the cover.");
            }

            Pair<InputStream, Long> data;
            try {
                data = getInputStreamAndLength(rootPath, zip, pack.cover.file);
            } catch (IOException ignored) {
                throw new StickerPackInvalidException("Could not find find " + pack.cover.file);
            }

            String contentType = Utils.getFileMimeType(new File(pack.cover.file), null);
            cover = new StickerInfo(data.first(), data.second(), Optional.fromNullable(pack.cover.emoji).or(""), contentType);
        }

        return new SignalServiceStickerManifestUpload(
                pack.title,
                pack.author,
                cover,
                stickers);
    }

    private static JsonStickerPack parseStickerPack(String rootPath, ZipFile zip) throws IOException {
        InputStream inputStream;
        if (zip != null) {
            inputStream = zip.getInputStream(zip.getEntry("manifest.json"));
        } else {
            inputStream = new FileInputStream((new File(rootPath, "manifest.json")));
        }
        return new ObjectMapper().readValue(inputStream, JsonStickerPack.class);
    }

    private static Pair<InputStream, Long> getInputStreamAndLength(final String rootPath, final ZipFile zip, final String subfile) throws IOException {
        if (zip != null) {
            final ZipEntry entry = zip.getEntry(subfile);
            return new Pair<>(zip.getInputStream(entry), entry.getSize());
        } else {
            final File file = new File(rootPath, subfile);
            return new Pair<>(new FileInputStream(file), file.length());
        }
    }

    void requestSyncGroups() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    void requestSyncContacts() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    void requestSyncBlocked() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.BLOCKED).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    void requestSyncConfiguration() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONFIGURATION).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    private byte[] getSenderCertificate() {
        // TODO support UUID capable sender certificates
        // byte[] certificate = accountManager.getSenderCertificateForPhoneNumberPrivacy();
        byte[] certificate;
        try {
            certificate = accountManager.getSenderCertificate();
        } catch (IOException e) {
            System.err.println("Failed to get sender certificate: " + e);
            return null;
        }
        // TODO cache for a day
        return certificate;
    }

    private byte[] getSelfUnidentifiedAccessKey() {
        return UnidentifiedAccess.deriveAccessKeyFrom(account.getProfileKey());
    }

    private byte[] getTargetUnidentifiedAccessKey(SignalServiceAddress recipient) {
        ProfileKey theirProfileKey = account.getProfileStore().getProfileKey(recipient);
        if (theirProfileKey == null) {
            return null;
        }
        SignalProfile targetProfile;
        try {
            targetProfile = getRecipientProfile(recipient, Optional.absent(), theirProfileKey);
        } catch (IOException e) {
            System.err.println("Failed to get recipient profile: " + e);
            return null;
        }

        if (targetProfile == null || targetProfile.getUnidentifiedAccess() == null) {
            return null;
        }

        if (targetProfile.isUnrestrictedUnidentifiedAccess()) {
            return KeyUtils.createUnrestrictedUnidentifiedAccess();
        }

        return UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey);
    }

    private Optional<UnidentifiedAccessPair> getAccessForSync() {
        byte[] selfUnidentifiedAccessKey = getSelfUnidentifiedAccessKey();
        byte[] selfUnidentifiedAccessCertificate = getSenderCertificate();

        if (selfUnidentifiedAccessKey == null || selfUnidentifiedAccessCertificate == null) {
            return Optional.absent();
        }

        try {
            return Optional.of(new UnidentifiedAccessPair(
                    new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate),
                    new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate)
            ));
        } catch (InvalidCertificateException e) {
            return Optional.absent();
        }
    }

    private List<Optional<UnidentifiedAccessPair>> getAccessFor(Collection<SignalServiceAddress> recipients) {
        List<Optional<UnidentifiedAccessPair>> result = new ArrayList<>(recipients.size());
        for (SignalServiceAddress recipient : recipients) {
            result.add(getAccessFor(recipient));
        }
        return result;
    }

    private Optional<UnidentifiedAccessPair> getAccessFor(SignalServiceAddress recipient) {
        byte[] recipientUnidentifiedAccessKey = getTargetUnidentifiedAccessKey(recipient);
        byte[] selfUnidentifiedAccessKey = getSelfUnidentifiedAccessKey();
        byte[] selfUnidentifiedAccessCertificate = getSenderCertificate();

        if (recipientUnidentifiedAccessKey == null || selfUnidentifiedAccessKey == null || selfUnidentifiedAccessCertificate == null) {
            return Optional.absent();
        }

        try {
            return Optional.of(new UnidentifiedAccessPair(
                    new UnidentifiedAccess(recipientUnidentifiedAccessKey, selfUnidentifiedAccessCertificate),
                    new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate)
            ));
        } catch (InvalidCertificateException e) {
            return Optional.absent();
        }
    }

    private Optional<UnidentifiedAccess> getUnidentifiedAccess(SignalServiceAddress recipient) {
        Optional<UnidentifiedAccessPair> unidentifiedAccess = getAccessFor(recipient);

        if (unidentifiedAccess.isPresent()) {
            return unidentifiedAccess.get().getTargetUnidentifiedAccess();
        }

        return Optional.absent();
    }

    private void sendSyncMessage(SignalServiceSyncMessage message)
            throws IOException, UntrustedIdentityException {
        SignalServiceMessageSender messageSender = getMessageSender();
        try {
            messageSender.sendMessage(message, getAccessForSync());
        } catch (UntrustedIdentityException e) {
            account.getSignalProtocolStore().saveIdentity(resolveSignalServiceAddress(e.getIdentifier()), e.getIdentityKey(), TrustLevel.UNTRUSTED);
            throw e;
        }
    }

    private Collection<SignalServiceAddress> getSignalServiceAddresses(Collection<String> numbers) throws InvalidNumberException {
        final Set<SignalServiceAddress> signalServiceAddresses = new HashSet<>(numbers.size());

        for (String number : numbers) {
            signalServiceAddresses.add(canonicalizeAndResolveSignalServiceAddress(number));
        }
        return signalServiceAddresses;
    }

    private Pair<Long, List<SendMessageResult>> sendMessage(SignalServiceDataMessage.Builder messageBuilder, Collection<SignalServiceAddress> recipients)
            throws IOException {
        recipients = recipients.stream().map(this::resolveSignalServiceAddress).collect(Collectors.toSet());
        final long timestamp = System.currentTimeMillis();
        messageBuilder.withTimestamp(timestamp);
        if (messagePipe == null) {
            messagePipe = getMessageReceiver().createMessagePipe();
        }
        if (unidentifiedMessagePipe == null) {
            unidentifiedMessagePipe = getMessageReceiver().createUnidentifiedMessagePipe();
        }
        SignalServiceDataMessage message = null;
        try {
            message = messageBuilder.build();
            if (message.getGroupContext().isPresent()) {
                try {
                    SignalServiceMessageSender messageSender = getMessageSender();
                    final boolean isRecipientUpdate = false;
                    List<SendMessageResult> result = messageSender.sendMessage(new ArrayList<>(recipients), getAccessFor(recipients), isRecipientUpdate, message);
                    for (SendMessageResult r : result) {
                        if (r.getIdentityFailure() != null) {
                            account.getSignalProtocolStore().saveIdentity(r.getAddress(), r.getIdentityFailure().getIdentityKey(), TrustLevel.UNTRUSTED);
                        }
                    }
                    return new Pair<>(timestamp, result);
                } catch (UntrustedIdentityException e) {
                    account.getSignalProtocolStore().saveIdentity(resolveSignalServiceAddress(e.getIdentifier()), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                    return new Pair<>(timestamp, Collections.emptyList());
                }
            } else {
                // Send to all individually, so sync messages are sent correctly
                List<SendMessageResult> results = new ArrayList<>(recipients.size());
                for (SignalServiceAddress address : recipients) {
                    ContactInfo contact = account.getContactStore().getContact(address);
                    if (contact != null) {
                        messageBuilder.withExpiration(contact.messageExpirationTime);
                        messageBuilder.withProfileKey(account.getProfileKey().serialize());
                    } else {
                        messageBuilder.withExpiration(0);
                        messageBuilder.withProfileKey(null);
                    }
                    message = messageBuilder.build();
                    if (address.matches(account.getSelfAddress())) {
                        results.add(sendSelfMessage(message));
                    } else {
                        results.add(sendMessage(address, message));
                    }
                }
                return new Pair<>(timestamp, results);
            }
        } finally {
            if (message != null && message.isEndSession()) {
                for (SignalServiceAddress recipient : recipients) {
                    handleEndSession(recipient);
                }
            }
            account.save();
        }
    }

    private SendMessageResult sendSelfMessage(SignalServiceDataMessage message) throws IOException {
        SignalServiceMessageSender messageSender = getMessageSender();

        SignalServiceAddress recipient = account.getSelfAddress();

        final Optional<UnidentifiedAccessPair> unidentifiedAccess = getAccessFor(recipient);
        SentTranscriptMessage transcript = new SentTranscriptMessage(Optional.of(recipient),
                message.getTimestamp(),
                message,
                message.getExpiresInSeconds(),
                Collections.singletonMap(recipient, unidentifiedAccess.isPresent()),
                false);
        SignalServiceSyncMessage syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);

        try {
            long startTime = System.currentTimeMillis();
            messageSender.sendMessage(syncMessage, unidentifiedAccess);
            return SendMessageResult.success(recipient, unidentifiedAccess.isPresent(), false, System.currentTimeMillis() - startTime);
        } catch (UntrustedIdentityException e) {
            account.getSignalProtocolStore().saveIdentity(resolveSignalServiceAddress(e.getIdentifier()), e.getIdentityKey(), TrustLevel.UNTRUSTED);
            return SendMessageResult.identityFailure(recipient, e.getIdentityKey());
        }
    }

    private SendMessageResult sendMessage(SignalServiceAddress address, SignalServiceDataMessage message) throws IOException {
        SignalServiceMessageSender messageSender = getMessageSender();

        try {
            return messageSender.sendMessage(address, getAccessFor(address), message);
        } catch (UntrustedIdentityException e) {
            account.getSignalProtocolStore().saveIdentity(resolveSignalServiceAddress(e.getIdentifier()), e.getIdentityKey(), TrustLevel.UNTRUSTED);
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        }
    }

    private SignalServiceContent decryptMessage(SignalServiceEnvelope envelope) throws InvalidMetadataMessageException, ProtocolInvalidMessageException, ProtocolDuplicateMessageException, ProtocolLegacyMessageException, ProtocolInvalidKeyIdException, InvalidMetadataVersionException, ProtocolInvalidVersionException, ProtocolNoSessionException, ProtocolInvalidKeyException, SelfSendException, UnsupportedDataMessageException, org.whispersystems.libsignal.UntrustedIdentityException {
        SignalServiceCipher cipher = new SignalServiceCipher(account.getSelfAddress(), account.getSignalProtocolStore(), Utils.getCertificateValidator());
        try {
            return cipher.decrypt(envelope);
        } catch (ProtocolUntrustedIdentityException e) {
            if (e.getCause() instanceof org.whispersystems.libsignal.UntrustedIdentityException) {
                org.whispersystems.libsignal.UntrustedIdentityException identityException = (org.whispersystems.libsignal.UntrustedIdentityException) e.getCause();
                account.getSignalProtocolStore().saveIdentity(resolveSignalServiceAddress(identityException.getName()), identityException.getUntrustedIdentity(), TrustLevel.UNTRUSTED);
                throw identityException;
            }
            throw new AssertionError(e);
        }
    }

    private void handleEndSession(SignalServiceAddress source) {
        account.getSignalProtocolStore().deleteAllSessions(source);
    }

    private static int currentTimeDays() {
        return (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    }

    private GroupsV2AuthorizationString getGroupAuthForToday(final GroupSecretParams groupSecretParams) throws IOException, VerificationFailedException {
        final int today = currentTimeDays();
        // Returns credentials for the next 7 days
        final HashMap<Integer, AuthCredentialResponse> credentials = groupsV2Api.getCredentials(today);
        // TODO cache credentials until they expire
        AuthCredentialResponse authCredentialResponse = credentials.get(today);
        return groupsV2Api.getGroupsV2AuthorizationString(account.getUuid(), today, groupSecretParams, authCredentialResponse);
    }

    private List<HandleAction> handleSignalServiceDataMessage(SignalServiceDataMessage message, boolean isSync, SignalServiceAddress source, SignalServiceAddress destination, boolean ignoreAttachments) {
        List<HandleAction> actions = new ArrayList<>();
        if (message.getGroupContext().isPresent()) {
            if (message.getGroupContext().get().getGroupV1().isPresent()) {
                SignalServiceGroup groupInfo = message.getGroupContext().get().getGroupV1().get();
                GroupInfo group = account.getGroupStore().getGroup(groupInfo.getGroupId());
                if (group == null || group instanceof GroupInfoV1) {
                    GroupInfoV1 groupV1 = (GroupInfoV1) group;
                    switch (groupInfo.getType()) {
                        case UPDATE: {
                            if (groupV1 == null) {
                                groupV1 = new GroupInfoV1(groupInfo.getGroupId());
                            }

                            if (groupInfo.getAvatar().isPresent()) {
                                SignalServiceAttachment avatar = groupInfo.getAvatar().get();
                                if (avatar.isPointer()) {
                                    try {
                                        retrieveGroupAvatarAttachment(avatar.asPointer(), groupV1.groupId);
                                    } catch (IOException | InvalidMessageException | MissingConfigurationException e) {
                                        System.err.println("Failed to retrieve group avatar (" + avatar.asPointer().getRemoteId() + "): " + e.getMessage());
                                    }
                                }
                            }

                            if (groupInfo.getName().isPresent()) {
                                groupV1.name = groupInfo.getName().get();
                            }

                            if (groupInfo.getMembers().isPresent()) {
                                groupV1.addMembers(groupInfo.getMembers().get()
                                        .stream()
                                        .map(this::resolveSignalServiceAddress)
                                        .collect(Collectors.toSet()));
                            }

                            account.getGroupStore().updateGroup(groupV1);
                            break;
                        }
                        case DELIVER:
                            if (groupV1 == null && !isSync) {
                                actions.add(new SendGroupInfoRequestAction(source, groupInfo.getGroupId()));
                            }
                            break;
                        case QUIT: {
                            if (groupV1 != null) {
                                groupV1.removeMember(source);
                                account.getGroupStore().updateGroup(groupV1);
                            }
                            break;
                        }
                        case REQUEST_INFO:
                            if (groupV1 != null && !isSync) {
                                actions.add(new SendGroupUpdateAction(source, groupV1.groupId));
                            }
                            break;
                    }
                } else {
                    System.err.println("Received a group v1 message for a v2 group: " + group.getTitle());
                }
            }
            if (message.getGroupContext().get().getGroupV2().isPresent()) {
                final SignalServiceGroupV2 groupContext = message.getGroupContext().get().getGroupV2().get();
                final GroupMasterKey groupMasterKey = groupContext.getMasterKey();

                final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

                byte[] groupId = groupSecretParams.getPublicParams().getGroupIdentifier().serialize();
                GroupInfo groupInfo = account.getGroupStore().getGroup(groupId);
                if (groupInfo instanceof GroupInfoV1) {
                    // TODO upgrade group
                } else if (groupInfo == null || groupInfo instanceof GroupInfoV2) {
                    GroupInfoV2 groupInfoV2 = groupInfo == null
                            ? new GroupInfoV2(groupId, groupMasterKey)
                            : (GroupInfoV2) groupInfo;

                    if (groupInfoV2.getGroup() == null || groupInfoV2.getGroup().getRevision() < groupContext.getRevision()) {
                        // TODO check if revision is only 1 behind and a signedGroupChange is available
                        try {
                            final GroupsV2AuthorizationString groupsV2AuthorizationString = getGroupAuthForToday(groupSecretParams);
                            final DecryptedGroup group = groupsV2Api.getGroup(groupSecretParams, groupsV2AuthorizationString);
                            groupInfoV2.setGroup(group);
                            for (DecryptedMember member : group.getMembersList()) {
                                final SignalServiceAddress address = resolveSignalServiceAddress(new SignalServiceAddress(UuidUtil.parseOrThrow(member.getUuid().toByteArray()), null));
                                try {
                                    account.getProfileStore().storeProfileKey(address, new ProfileKey(member.getProfileKey().toByteArray()));
                                } catch (InvalidInputException ignored) {
                                }
                            }
                        } catch (IOException | VerificationFailedException | InvalidGroupStateException e) {
                            System.err.println("Failed to retrieve Group V2 info, ignoring ...");
                        }
                        account.getGroupStore().updateGroup(groupInfoV2);
                    }
                }
            }
        }
        final SignalServiceAddress conversationPartnerAddress = isSync ? destination : source;
        if (message.isEndSession()) {
            handleEndSession(conversationPartnerAddress);
        }
        if (message.isExpirationUpdate() || message.getBody().isPresent()) {
            if (message.getGroupContext().isPresent()) {
                if (message.getGroupContext().get().getGroupV1().isPresent()) {
                    SignalServiceGroup groupInfo = message.getGroupContext().get().getGroupV1().get();
                    GroupInfoV1 group = account.getGroupStore().getOrCreateGroupV1(groupInfo.getGroupId());
                    if (group != null) {
                        if (group.messageExpirationTime != message.getExpiresInSeconds()) {
                            group.messageExpirationTime = message.getExpiresInSeconds();
                            account.getGroupStore().updateGroup(group);
                        }
                    }
                } else if (message.getGroupContext().get().getGroupV2().isPresent()) {
                    // disappearing message timer already stored in the DecryptedGroup
                }
            } else {
                ContactInfo contact = account.getContactStore().getContact(conversationPartnerAddress);
                if (contact == null) {
                    contact = new ContactInfo(conversationPartnerAddress);
                }
                if (contact.messageExpirationTime != message.getExpiresInSeconds()) {
                    contact.messageExpirationTime = message.getExpiresInSeconds();
                    account.getContactStore().updateContact(contact);
                }
            }
        }
        if (message.getAttachments().isPresent() && !ignoreAttachments) {
            for (SignalServiceAttachment attachment : message.getAttachments().get()) {
                if (attachment.isPointer()) {
                    try {
                        retrieveAttachment(attachment.asPointer());
                    } catch (IOException | InvalidMessageException | MissingConfigurationException e) {
                        System.err.println("Failed to retrieve attachment (" + attachment.asPointer().getRemoteId() + "): " + e.getMessage());
                    }
                }
            }
        }
        if (message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
            final ProfileKey profileKey;
            try {
                profileKey = new ProfileKey(message.getProfileKey().get());
            } catch (InvalidInputException e) {
                throw new AssertionError(e);
            }
            if (source.matches(account.getSelfAddress())) {
                this.account.setProfileKey(profileKey);
            }
            this.account.getProfileStore().storeProfileKey(source, profileKey);
        }
        if (message.getPreviews().isPresent()) {
            final List<SignalServiceDataMessage.Preview> previews = message.getPreviews().get();
            for (SignalServiceDataMessage.Preview preview : previews) {
                if (preview.getImage().isPresent() && preview.getImage().get().isPointer()) {
                    SignalServiceAttachmentPointer attachment = preview.getImage().get().asPointer();
                    try {
                        retrieveAttachment(attachment);
                    } catch (IOException | InvalidMessageException | MissingConfigurationException e) {
                        System.err.println("Failed to retrieve attachment (" + attachment.getRemoteId() + "): " + e.getMessage());
                    }
                }
            }
        }
        if (message.getSticker().isPresent()) {
            final SignalServiceDataMessage.Sticker messageSticker = message.getSticker().get();
            Sticker sticker = account.getStickerStore().getSticker(messageSticker.getPackId());
            if (sticker == null) {
                sticker = new Sticker(messageSticker.getPackId(), messageSticker.getPackKey());
                account.getStickerStore().updateSticker(sticker);
            }
        }
        return actions;
    }

    private void retryFailedReceivedMessages(ReceiveMessageHandler handler, boolean ignoreAttachments) {
        final File cachePath = new File(getMessageCachePath());
        if (!cachePath.exists()) {
            return;
        }
        for (final File dir : Objects.requireNonNull(cachePath.listFiles())) {
            if (!dir.isDirectory()) {
                retryFailedReceivedMessage(handler, ignoreAttachments, dir);
                continue;
            }

            for (final File fileEntry : Objects.requireNonNull(dir.listFiles())) {
                if (!fileEntry.isFile()) {
                    continue;
                }
                retryFailedReceivedMessage(handler, ignoreAttachments, fileEntry);
            }
            // Try to delete directory if empty
            dir.delete();
        }
    }

    private void retryFailedReceivedMessage(final ReceiveMessageHandler handler, final boolean ignoreAttachments, final File fileEntry) {
        SignalServiceEnvelope envelope;
        try {
            envelope = Utils.loadEnvelope(fileEntry);
            if (envelope == null) {
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        SignalServiceContent content = null;
        if (!envelope.isReceipt()) {
            try {
                content = decryptMessage(envelope);
            } catch (org.whispersystems.libsignal.UntrustedIdentityException e) {
                return;
            } catch (Exception er) {
                // All other errors are not recoverable, so delete the cached message
                try {
                    Files.delete(fileEntry.toPath());
                } catch (IOException e) {
                    System.err.println("Failed to delete cached message file “" + fileEntry + "”: " + e.getMessage());
                }
                return;
            }
            List<HandleAction> actions = handleMessage(envelope, content, ignoreAttachments);
            for (HandleAction action : actions) {
                try {
                    action.execute(this);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        account.save();
        handler.handleMessage(envelope, content, null);
        try {
            Files.delete(fileEntry.toPath());
        } catch (IOException e) {
            System.err.println("Failed to delete cached message file “" + fileEntry + "”: " + e.getMessage());
        }
    }

    public void receiveMessages(long timeout, TimeUnit unit, boolean returnOnTimeout, boolean ignoreAttachments, ReceiveMessageHandler handler) throws IOException {
        retryFailedReceivedMessages(handler, ignoreAttachments);
        final SignalServiceMessageReceiver messageReceiver = getMessageReceiver();

        Set<HandleAction> queuedActions = null;

        if (messagePipe == null) {
            messagePipe = messageReceiver.createMessagePipe();
        }

        boolean hasCaughtUpWithOldMessages = false;

        while (true) {
            SignalServiceEnvelope envelope;
            SignalServiceContent content = null;
            Exception exception = null;
            final long now = new Date().getTime();
            try {
                Optional<SignalServiceEnvelope> result = messagePipe.readOrEmpty(timeout, unit, envelope1 -> {
                    // store message on disk, before acknowledging receipt to the server
                    try {
                        String source = envelope1.getSourceE164().isPresent() ? envelope1.getSourceE164().get() : "";
                        File cacheFile = getMessageCacheFile(source, now, envelope1.getTimestamp());
                        Utils.storeEnvelope(envelope1, cacheFile);
                    } catch (IOException e) {
                        System.err.println("Failed to store encrypted message in disk cache, ignoring: " + e.getMessage());
                    }
                });
                if (result.isPresent()) {
                    envelope = result.get();
                } else {
                    // Received indicator that server queue is empty
                    hasCaughtUpWithOldMessages = true;

                    if (queuedActions != null) {
                        for (HandleAction action : queuedActions) {
                            try {
                                action.execute(this);
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                        account.save();
                        queuedActions.clear();
                        queuedActions = null;
                    }

                    // Continue to wait another timeout for new messages
                    continue;
                }
            } catch (TimeoutException e) {
                if (returnOnTimeout)
                    return;
                continue;
            } catch (InvalidVersionException e) {
                System.err.println("Ignoring error: " + e.getMessage());
                continue;
            }

            if (envelope.hasSource()) {
                // Store uuid if we don't have it already
                SignalServiceAddress source = envelope.getSourceAddress();
                resolveSignalServiceAddress(source);
            }
            if (!envelope.isReceipt()) {
                try {
                    content = decryptMessage(envelope);
                } catch (Exception e) {
                    exception = e;
                }
                List<HandleAction> actions = handleMessage(envelope, content, ignoreAttachments);
                if (hasCaughtUpWithOldMessages) {
                    for (HandleAction action : actions) {
                        try {
                            action.execute(this);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    if (queuedActions == null) {
                        queuedActions = new HashSet<>();
                    }
                    queuedActions.addAll(actions);
                }
            }
            account.save();
            if (!isMessageBlocked(envelope, content)) {
                handler.handleMessage(envelope, content, exception);
            }
            if (!(exception instanceof org.whispersystems.libsignal.UntrustedIdentityException)) {
                File cacheFile = null;
                try {
                    String source = envelope.getSourceE164().isPresent() ? envelope.getSourceE164().get() : "";
                    cacheFile = getMessageCacheFile(source, now, envelope.getTimestamp());
                    Files.delete(cacheFile.toPath());
                    // Try to delete directory if empty
                    new File(getMessageCachePath()).delete();
                } catch (IOException e) {
                    System.err.println("Failed to delete cached message file “" + cacheFile + "”: " + e.getMessage());
                }
            }
        }
    }

    private boolean isMessageBlocked(SignalServiceEnvelope envelope, SignalServiceContent content) {
        SignalServiceAddress source;
        if (!envelope.isUnidentifiedSender() && envelope.hasSource()) {
            source = envelope.getSourceAddress();
        } else if (content != null) {
            source = content.getSender();
        } else {
            return false;
        }
        ContactInfo sourceContact = account.getContactStore().getContact(source);
        if (sourceContact != null && sourceContact.blocked) {
            return true;
        }

        if (content != null && content.getDataMessage().isPresent()) {
            SignalServiceDataMessage message = content.getDataMessage().get();
            if (message.getGroupContext().isPresent() && message.getGroupContext().get().getGroupV1().isPresent()) {
                SignalServiceGroup groupInfo = message.getGroupContext().get().getGroupV1().get();
                GroupInfo group = getGroup(groupInfo.getGroupId());
                return groupInfo.getType() == SignalServiceGroup.Type.DELIVER && group != null && group.isBlocked();
            }
        }
        return false;
    }

    private List<HandleAction> handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments) {
        List<HandleAction> actions = new ArrayList<>();
        if (content != null) {
            SignalServiceAddress sender;
            if (!envelope.isUnidentifiedSender() && envelope.hasSource()) {
                sender = envelope.getSourceAddress();
            } else {
                sender = content.getSender();
            }
            // Store uuid if we don't have it already
            resolveSignalServiceAddress(sender);

            if (content.getDataMessage().isPresent()) {
                SignalServiceDataMessage message = content.getDataMessage().get();

                if (content.isNeedsReceipt()) {
                    actions.add(new SendReceiptAction(sender, message.getTimestamp()));
                }

                actions.addAll(handleSignalServiceDataMessage(message, false, sender, account.getSelfAddress(), ignoreAttachments));
            }
            if (content.getSyncMessage().isPresent()) {
                account.setMultiDevice(true);
                SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();
                if (syncMessage.getSent().isPresent()) {
                    SentTranscriptMessage message = syncMessage.getSent().get();
                    actions.addAll(handleSignalServiceDataMessage(message.getMessage(), true, sender, message.getDestination().orNull(), ignoreAttachments));
                }
                if (syncMessage.getRequest().isPresent()) {
                    RequestMessage rm = syncMessage.getRequest().get();
                    if (rm.isContactsRequest()) {
                        actions.add(SendSyncContactsAction.create());
                    }
                    if (rm.isGroupsRequest()) {
                        actions.add(SendSyncGroupsAction.create());
                    }
                    if (rm.isBlockedListRequest()) {
                        actions.add(SendSyncBlockedListAction.create());
                    }
                    // TODO Handle rm.isConfigurationRequest(); rm.isKeysRequest();
                }
                if (syncMessage.getGroups().isPresent()) {
                    File tmpFile = null;
                    try {
                        tmpFile = IOUtils.createTempFile();
                        try (InputStream attachmentAsStream = retrieveAttachmentAsStream(syncMessage.getGroups().get().asPointer(), tmpFile)) {
                            DeviceGroupsInputStream s = new DeviceGroupsInputStream(attachmentAsStream);
                            DeviceGroup g;
                            while ((g = s.read()) != null) {
                                GroupInfoV1 syncGroup = account.getGroupStore().getOrCreateGroupV1(g.getId());
                                if (syncGroup != null) {
                                    if (g.getName().isPresent()) {
                                        syncGroup.name = g.getName().get();
                                    }
                                    syncGroup.addMembers(g.getMembers()
                                            .stream()
                                            .map(this::resolveSignalServiceAddress)
                                            .collect(Collectors.toSet()));
                                    if (!g.isActive()) {
                                        syncGroup.removeMember(account.getSelfAddress());
                                    } else {
                                        // Add ourself to the member set as it's marked as active
                                        syncGroup.addMembers(Collections.singleton(account.getSelfAddress()));
                                    }
                                    syncGroup.blocked = g.isBlocked();
                                    if (g.getColor().isPresent()) {
                                        syncGroup.color = g.getColor().get();
                                    }

                                    if (g.getAvatar().isPresent()) {
                                        retrieveGroupAvatarAttachment(g.getAvatar().get(), syncGroup.groupId);
                                    }
                                    syncGroup.inboxPosition = g.getInboxPosition().orNull();
                                    syncGroup.archived = g.isArchived();
                                    account.getGroupStore().updateGroup(syncGroup);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (tmpFile != null) {
                            try {
                                Files.delete(tmpFile.toPath());
                            } catch (IOException e) {
                                System.err.println("Failed to delete received groups temp file “" + tmpFile + "”: " + e.getMessage());
                            }
                        }
                    }
                }
                if (syncMessage.getBlockedList().isPresent()) {
                    final BlockedListMessage blockedListMessage = syncMessage.getBlockedList().get();
                    for (SignalServiceAddress address : blockedListMessage.getAddresses()) {
                        setContactBlocked(resolveSignalServiceAddress(address), true);
                    }
                    for (byte[] groupId : blockedListMessage.getGroupIds()) {
                        try {
                            setGroupBlocked(groupId, true);
                        } catch (GroupNotFoundException e) {
                            System.err.println("BlockedListMessage contained groupID that was not found in GroupStore: " + Base64.encodeBytes(groupId));
                        }
                    }
                }
                if (syncMessage.getContacts().isPresent()) {
                    File tmpFile = null;
                    try {
                        tmpFile = IOUtils.createTempFile();
                        final ContactsMessage contactsMessage = syncMessage.getContacts().get();
                        try (InputStream attachmentAsStream = retrieveAttachmentAsStream(contactsMessage.getContactsStream().asPointer(), tmpFile)) {
                            DeviceContactsInputStream s = new DeviceContactsInputStream(attachmentAsStream);
                            if (contactsMessage.isComplete()) {
                                account.getContactStore().clear();
                            }
                            DeviceContact c;
                            while ((c = s.read()) != null) {
                                if (c.getAddress().matches(account.getSelfAddress()) && c.getProfileKey().isPresent()) {
                                    account.setProfileKey(c.getProfileKey().get());
                                }
                                final SignalServiceAddress address = resolveSignalServiceAddress(c.getAddress());
                                ContactInfo contact = account.getContactStore().getContact(address);
                                if (contact == null) {
                                    contact = new ContactInfo(address);
                                }
                                if (c.getName().isPresent()) {
                                    contact.name = c.getName().get();
                                }
                                if (c.getColor().isPresent()) {
                                    contact.color = c.getColor().get();
                                }
                                if (c.getProfileKey().isPresent()) {
                                    account.getProfileStore().storeProfileKey(address, c.getProfileKey().get());
                                }
                                if (c.getVerified().isPresent()) {
                                    final VerifiedMessage verifiedMessage = c.getVerified().get();
                                    account.getSignalProtocolStore().setIdentityTrustLevel(verifiedMessage.getDestination(), verifiedMessage.getIdentityKey(), TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
                                }
                                if (c.getExpirationTimer().isPresent()) {
                                    contact.messageExpirationTime = c.getExpirationTimer().get();
                                }
                                contact.blocked = c.isBlocked();
                                contact.inboxPosition = c.getInboxPosition().orNull();
                                contact.archived = c.isArchived();
                                account.getContactStore().updateContact(contact);

                                if (c.getAvatar().isPresent()) {
                                    retrieveContactAvatarAttachment(c.getAvatar().get(), contact.number);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (tmpFile != null) {
                            try {
                                Files.delete(tmpFile.toPath());
                            } catch (IOException e) {
                                System.err.println("Failed to delete received contacts temp file “" + tmpFile + "”: " + e.getMessage());
                            }
                        }
                    }
                }
                if (syncMessage.getVerified().isPresent()) {
                    final VerifiedMessage verifiedMessage = syncMessage.getVerified().get();
                    account.getSignalProtocolStore().setIdentityTrustLevel(resolveSignalServiceAddress(verifiedMessage.getDestination()), verifiedMessage.getIdentityKey(), TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
                }
                if (syncMessage.getStickerPackOperations().isPresent()) {
                    final List<StickerPackOperationMessage> stickerPackOperationMessages = syncMessage.getStickerPackOperations().get();
                    for (StickerPackOperationMessage m : stickerPackOperationMessages) {
                        if (!m.getPackId().isPresent()) {
                            continue;
                        }
                        Sticker sticker = account.getStickerStore().getSticker(m.getPackId().get());
                        if (sticker == null) {
                            if (!m.getPackKey().isPresent()) {
                                continue;
                            }
                            sticker = new Sticker(m.getPackId().get(), m.getPackKey().get());
                        }
                        sticker.setInstalled(!m.getType().isPresent() || m.getType().get() == StickerPackOperationMessage.Type.INSTALL);
                        account.getStickerStore().updateSticker(sticker);
                    }
                }
                if (syncMessage.getConfiguration().isPresent()) {
                    // TODO
                }
            }
        }
        return actions;
    }

    private File getContactAvatarFile(String number) {
        return new File(pathConfig.getAvatarsPath(), "contact-" + number);
    }

    private File retrieveContactAvatarAttachment(SignalServiceAttachment attachment, String number) throws IOException, InvalidMessageException, MissingConfigurationException {
        IOUtils.createPrivateDirectories(pathConfig.getAvatarsPath());
        if (attachment.isPointer()) {
            SignalServiceAttachmentPointer pointer = attachment.asPointer();
            return retrieveAttachment(pointer, getContactAvatarFile(number), false);
        } else {
            SignalServiceAttachmentStream stream = attachment.asStream();
            return Utils.retrieveAttachment(stream, getContactAvatarFile(number));
        }
    }

    private File getGroupAvatarFile(byte[] groupId) {
        return new File(pathConfig.getAvatarsPath(), "group-" + Base64.encodeBytes(groupId).replace("/", "_"));
    }

    private File retrieveGroupAvatarAttachment(SignalServiceAttachment attachment, byte[] groupId) throws IOException, InvalidMessageException, MissingConfigurationException {
        IOUtils.createPrivateDirectories(pathConfig.getAvatarsPath());
        if (attachment.isPointer()) {
            SignalServiceAttachmentPointer pointer = attachment.asPointer();
            return retrieveAttachment(pointer, getGroupAvatarFile(groupId), false);
        } else {
            SignalServiceAttachmentStream stream = attachment.asStream();
            return Utils.retrieveAttachment(stream, getGroupAvatarFile(groupId));
        }
    }

    private File getProfileAvatarFile(SignalServiceAddress address) {
        return new File(pathConfig.getAvatarsPath(), "profile-" + address.getLegacyIdentifier());
    }

    private File retrieveProfileAvatar(SignalServiceAddress address, String avatarPath, ProfileKey profileKey) throws IOException {
        IOUtils.createPrivateDirectories(pathConfig.getAvatarsPath());
        SignalServiceMessageReceiver receiver = getMessageReceiver();
        File outputFile = getProfileAvatarFile(address);

        File tmpFile = IOUtils.createTempFile();
        try (InputStream input = receiver.retrieveProfileAvatar(avatarPath, tmpFile, profileKey, ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE)) {
            // Use larger buffer size to prevent AssertionError: Need: 12272 but only have: 8192 ...
            IOUtils.copyStreamToFile(input, outputFile, (int) ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE);
        } finally {
            try {
                Files.delete(tmpFile.toPath());
            } catch (IOException e) {
                System.err.println("Failed to delete received avatar temp file “" + tmpFile + "”: " + e.getMessage());
            }
        }
        return outputFile;
    }

    public File getAttachmentFile(SignalServiceAttachmentRemoteId attachmentId) {
        return new File(pathConfig.getAttachmentsPath(), attachmentId.toString());
    }

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer) throws IOException, InvalidMessageException, MissingConfigurationException {
        IOUtils.createPrivateDirectories(pathConfig.getAttachmentsPath());
        return retrieveAttachment(pointer, getAttachmentFile(pointer.getRemoteId()), true);
    }

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer, File outputFile, boolean storePreview) throws IOException, InvalidMessageException, MissingConfigurationException {
        if (storePreview && pointer.getPreview().isPresent()) {
            File previewFile = new File(outputFile + ".preview");
            try (OutputStream output = new FileOutputStream(previewFile)) {
                byte[] preview = pointer.getPreview().get();
                output.write(preview, 0, preview.length);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }

        final SignalServiceMessageReceiver messageReceiver = getMessageReceiver();

        File tmpFile = IOUtils.createTempFile();
        try (InputStream input = messageReceiver.retrieveAttachment(pointer, tmpFile, ServiceConfig.MAX_ATTACHMENT_SIZE)) {
            IOUtils.copyStreamToFile(input, outputFile);
        } finally {
            try {
                Files.delete(tmpFile.toPath());
            } catch (IOException e) {
                System.err.println("Failed to delete received attachment temp file “" + tmpFile + "”: " + e.getMessage());
            }
        }
        return outputFile;
    }

    private InputStream retrieveAttachmentAsStream(SignalServiceAttachmentPointer pointer, File tmpFile) throws IOException, InvalidMessageException, MissingConfigurationException {
        final SignalServiceMessageReceiver messageReceiver = getMessageReceiver();
        return messageReceiver.retrieveAttachment(pointer, tmpFile, ServiceConfig.MAX_ATTACHMENT_SIZE);
    }

    void sendGroups() throws IOException, UntrustedIdentityException {
        File groupsFile = IOUtils.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(groupsFile)) {
                DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(fos);
                for (GroupInfo record : account.getGroupStore().getGroups()) {
                    if (record instanceof GroupInfoV1) {
                        GroupInfoV1 groupInfo = (GroupInfoV1) record;
                        out.write(new DeviceGroup(groupInfo.groupId, Optional.fromNullable(groupInfo.name),
                                new ArrayList<>(groupInfo.getMembers()), createGroupAvatarAttachment(groupInfo.groupId),
                                groupInfo.isMember(account.getSelfAddress()), Optional.of(groupInfo.messageExpirationTime),
                                Optional.fromNullable(groupInfo.color), groupInfo.blocked, Optional.fromNullable(groupInfo.inboxPosition), groupInfo.archived));
                    }
                }
            }

            if (groupsFile.exists() && groupsFile.length() > 0) {
                try (FileInputStream groupsFileStream = new FileInputStream(groupsFile)) {
                    SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                            .withStream(groupsFileStream)
                            .withContentType("application/octet-stream")
                            .withLength(groupsFile.length())
                            .build();

                    sendSyncMessage(SignalServiceSyncMessage.forGroups(attachmentStream));
                }
            }
        } finally {
            try {
                Files.delete(groupsFile.toPath());
            } catch (IOException e) {
                System.err.println("Failed to delete groups temp file “" + groupsFile + "”: " + e.getMessage());
            }
        }
    }

    public void sendContacts() throws IOException, UntrustedIdentityException {
        File contactsFile = IOUtils.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(contactsFile)) {
                DeviceContactsOutputStream out = new DeviceContactsOutputStream(fos);
                for (ContactInfo record : account.getContactStore().getContacts()) {
                    VerifiedMessage verifiedMessage = null;
                    JsonIdentityKeyStore.Identity currentIdentity = account.getSignalProtocolStore().getIdentity(record.getAddress());
                    if (currentIdentity != null) {
                        verifiedMessage = new VerifiedMessage(record.getAddress(), currentIdentity.getIdentityKey(), currentIdentity.getTrustLevel().toVerifiedState(), currentIdentity.getDateAdded().getTime());
                    }

                    ProfileKey profileKey = account.getProfileStore().getProfileKey(record.getAddress());
                    out.write(new DeviceContact(record.getAddress(), Optional.fromNullable(record.name),
                            createContactAvatarAttachment(record.number), Optional.fromNullable(record.color),
                            Optional.fromNullable(verifiedMessage), Optional.fromNullable(profileKey), record.blocked,
                            Optional.of(record.messageExpirationTime),
                            Optional.fromNullable(record.inboxPosition), record.archived));
                }

                if (account.getProfileKey() != null) {
                    // Send our own profile key as well
                    out.write(new DeviceContact(account.getSelfAddress(),
                            Optional.absent(), Optional.absent(),
                            Optional.absent(), Optional.absent(),
                            Optional.of(account.getProfileKey()),
                            false, Optional.absent(), Optional.absent(), false));
                }
            }

            if (contactsFile.exists() && contactsFile.length() > 0) {
                try (FileInputStream contactsFileStream = new FileInputStream(contactsFile)) {
                    SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                            .withStream(contactsFileStream)
                            .withContentType("application/octet-stream")
                            .withLength(contactsFile.length())
                            .build();

                    sendSyncMessage(SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, true)));
                }
            }
        } finally {
            try {
                Files.delete(contactsFile.toPath());
            } catch (IOException e) {
                System.err.println("Failed to delete contacts temp file “" + contactsFile + "”: " + e.getMessage());
            }
        }
    }

    void sendBlockedList() throws IOException, UntrustedIdentityException {
        List<SignalServiceAddress> addresses = new ArrayList<>();
        for (ContactInfo record : account.getContactStore().getContacts()) {
            if (record.blocked) {
                addresses.add(record.getAddress());
            }
        }
        List<byte[]> groupIds = new ArrayList<>();
        for (GroupInfo record : account.getGroupStore().getGroups()) {
            if (record.isBlocked()) {
                groupIds.add(record.groupId);
            }
        }
        sendSyncMessage(SignalServiceSyncMessage.forBlocked(new BlockedListMessage(addresses, groupIds)));
    }

    private void sendVerifiedMessage(SignalServiceAddress destination, IdentityKey identityKey, TrustLevel trustLevel) throws IOException, UntrustedIdentityException {
        VerifiedMessage verifiedMessage = new VerifiedMessage(destination, identityKey, trustLevel.toVerifiedState(), System.currentTimeMillis());
        sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
    }

    public List<ContactInfo> getContacts() {
        return account.getContactStore().getContacts();
    }

    public ContactInfo getContact(String number) {
        return account.getContactStore().getContact(Util.getSignalServiceAddressFromIdentifier(number));
    }

    public GroupInfo getGroup(byte[] groupId) {
        return account.getGroupStore().getGroup(groupId);
    }

    public byte[] getGroupId(GroupMasterKey groupMasterKey) {
        final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
        return groupSecretParams.getPublicParams().getGroupIdentifier().serialize();
    }

    public List<JsonIdentityKeyStore.Identity> getIdentities() {
        return account.getSignalProtocolStore().getIdentities();
    }

    public List<JsonIdentityKeyStore.Identity> getIdentities(String number) throws InvalidNumberException {
        return account.getSignalProtocolStore().getIdentities(canonicalizeAndResolveSignalServiceAddress(number));
    }

    /**
     * Trust this the identity with this fingerprint
     *
     * @param name        username of the identity
     * @param fingerprint Fingerprint
     */
    public boolean trustIdentityVerified(String name, byte[] fingerprint) throws InvalidNumberException {
        SignalServiceAddress address = canonicalizeAndResolveSignalServiceAddress(name);
        List<JsonIdentityKeyStore.Identity> ids = account.getSignalProtocolStore().getIdentities(address);
        if (ids == null) {
            return false;
        }
        for (JsonIdentityKeyStore.Identity id : ids) {
            if (!Arrays.equals(id.getIdentityKey().serialize(), fingerprint)) {
                continue;
            }

            account.getSignalProtocolStore().setIdentityTrustLevel(address, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            try {
                sendVerifiedMessage(address, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            } catch (IOException | UntrustedIdentityException e) {
                e.printStackTrace();
            }
            account.save();
            return true;
        }
        return false;
    }

    /**
     * Trust this the identity with this safety number
     *
     * @param name         username of the identity
     * @param safetyNumber Safety number
     */
    public boolean trustIdentityVerifiedSafetyNumber(String name, String safetyNumber) throws InvalidNumberException {
        SignalServiceAddress address = canonicalizeAndResolveSignalServiceAddress(name);
        List<JsonIdentityKeyStore.Identity> ids = account.getSignalProtocolStore().getIdentities(address);
        if (ids == null) {
            return false;
        }
        for (JsonIdentityKeyStore.Identity id : ids) {
            if (!safetyNumber.equals(computeSafetyNumber(address, id.getIdentityKey()))) {
                continue;
            }

            account.getSignalProtocolStore().setIdentityTrustLevel(address, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            try {
                sendVerifiedMessage(address, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            } catch (IOException | UntrustedIdentityException e) {
                e.printStackTrace();
            }
            account.save();
            return true;
        }
        return false;
    }

    /**
     * Trust all keys of this identity without verification
     *
     * @param name username of the identity
     */
    public boolean trustIdentityAllKeys(String name) {
        SignalServiceAddress address = resolveSignalServiceAddress(name);
        List<JsonIdentityKeyStore.Identity> ids = account.getSignalProtocolStore().getIdentities(address);
        if (ids == null) {
            return false;
        }
        for (JsonIdentityKeyStore.Identity id : ids) {
            if (id.getTrustLevel() == TrustLevel.UNTRUSTED) {
                account.getSignalProtocolStore().setIdentityTrustLevel(address, id.getIdentityKey(), TrustLevel.TRUSTED_UNVERIFIED);
                try {
                    sendVerifiedMessage(address, id.getIdentityKey(), TrustLevel.TRUSTED_UNVERIFIED);
                } catch (IOException | UntrustedIdentityException e) {
                    e.printStackTrace();
                }
            }
        }
        account.save();
        return true;
    }

    public String computeSafetyNumber(SignalServiceAddress theirAddress, IdentityKey theirIdentityKey) {
        return Utils.computeSafetyNumber(account.getSelfAddress(), getIdentityKeyPair().getPublicKey(), theirAddress, theirIdentityKey);
    }

    void saveAccount() {
        account.save();
    }

    public SignalServiceAddress canonicalizeAndResolveSignalServiceAddress(String identifier) throws InvalidNumberException {
        String canonicalizedNumber = UuidUtil.isUuid(identifier) ? identifier : Util.canonicalizeNumber(identifier, account.getUsername());
        return resolveSignalServiceAddress(canonicalizedNumber);
    }

    public SignalServiceAddress resolveSignalServiceAddress(String identifier) {
        SignalServiceAddress address = Util.getSignalServiceAddressFromIdentifier(identifier);

        return resolveSignalServiceAddress(address);
    }

    public SignalServiceAddress resolveSignalServiceAddress(SignalServiceAddress address) {
        if (address.matches(account.getSelfAddress())) {
            return account.getSelfAddress();
        }

        return account.getRecipientStore().resolveServiceAddress(address);
    }

    @Override
    public void close() throws IOException {
        if (messagePipe != null) {
            messagePipe.shutdown();
            messagePipe = null;
        }

        if (unidentifiedMessagePipe != null) {
            unidentifiedMessagePipe.shutdown();
            unidentifiedMessagePipe = null;
        }

        account.close();
    }

    public interface ReceiveMessageHandler {

        void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e);
    }
}
