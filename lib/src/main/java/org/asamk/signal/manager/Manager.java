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

import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdV1;
import org.asamk.signal.manager.groups.GroupIdV2;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.helper.GroupHelper;
import org.asamk.signal.manager.helper.PinHelper;
import org.asamk.signal.manager.helper.ProfileHelper;
import org.asamk.signal.manager.helper.UnidentifiedAccessHelper;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.contacts.ContactInfo;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.storage.messageCache.CachedMessage;
import org.asamk.signal.manager.storage.profiles.SignalProfile;
import org.asamk.signal.manager.storage.profiles.SignalProfileEntry;
import org.asamk.signal.manager.storage.protocol.IdentityInfo;
import org.asamk.signal.manager.storage.stickers.Sticker;
import org.asamk.signal.manager.util.AttachmentUtils;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.KeyUtils;
import org.asamk.signal.manager.util.ProfileUtils;
import org.asamk.signal.manager.util.StickerUtils;
import org.asamk.signal.manager.util.Utils;
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
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.kbs.MasterKey;
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
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.messages.multidevice.KeysMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SignatureException;
import java.util.ArrayList;
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
import java.util.stream.Collectors;

import static org.asamk.signal.manager.config.ServiceConfig.capabilities;

public class Manager implements Closeable {

    private final static Logger logger = LoggerFactory.getLogger(Manager.class);

    private final CertificateValidator certificateValidator;

    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;

    private SignalAccount account;
    private final SignalServiceAccountManager accountManager;
    private final GroupsV2Api groupsV2Api;
    private final GroupsV2Operations groupsV2Operations;
    private final SignalServiceMessageReceiver messageReceiver;
    private final ClientZkProfileOperations clientZkProfileOperations;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private SignalServiceMessagePipe messagePipe = null;
    private SignalServiceMessagePipe unidentifiedMessagePipe = null;

    private final UnidentifiedAccessHelper unidentifiedAccessHelper;
    private final ProfileHelper profileHelper;
    private final GroupHelper groupHelper;
    private final PinHelper pinHelper;
    private final AvatarStore avatarStore;
    private final AttachmentStore attachmentStore;

    Manager(
            SignalAccount account,
            PathConfig pathConfig,
            ServiceEnvironmentConfig serviceEnvironmentConfig,
            String userAgent
    ) {
        this.account = account;
        this.serviceEnvironmentConfig = serviceEnvironmentConfig;
        this.certificateValidator = new CertificateValidator(serviceEnvironmentConfig.getUnidentifiedSenderTrustRoot());
        this.userAgent = userAgent;
        this.groupsV2Operations = capabilities.isGv2() ? new GroupsV2Operations(ClientZkOperations.create(
                serviceEnvironmentConfig.getSignalServiceConfiguration())) : null;
        final SleepTimer timer = new UptimeSleepTimer();
        this.accountManager = new SignalServiceAccountManager(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                new DynamicCredentialsProvider(account.getUuid(),
                        account.getUsername(),
                        account.getPassword(),
                        account.getDeviceId()),
                userAgent,
                groupsV2Operations,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY,
                timer);
        this.groupsV2Api = accountManager.getGroupsV2Api();
        final KeyBackupService keyBackupService = accountManager.getKeyBackupService(ServiceConfig.getIasKeyStore(),
                serviceEnvironmentConfig.getKeyBackupConfig().getEnclaveName(),
                serviceEnvironmentConfig.getKeyBackupConfig().getServiceId(),
                serviceEnvironmentConfig.getKeyBackupConfig().getMrenclave(),
                10);

        this.pinHelper = new PinHelper(keyBackupService);
        this.clientZkProfileOperations = capabilities.isGv2()
                ? ClientZkOperations.create(serviceEnvironmentConfig.getSignalServiceConfiguration())
                .getProfileOperations()
                : null;
        this.messageReceiver = new SignalServiceMessageReceiver(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                account.getUuid(),
                account.getUsername(),
                account.getPassword(),
                account.getDeviceId(),
                userAgent,
                null,
                timer,
                clientZkProfileOperations,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY);

        this.account.setResolver(this::resolveSignalServiceAddress);

        this.unidentifiedAccessHelper = new UnidentifiedAccessHelper(account::getProfileKey,
                account.getProfileStore()::getProfileKey,
                this::getRecipientProfile,
                this::getSenderCertificate);
        this.profileHelper = new ProfileHelper(account.getProfileStore()::getProfileKey,
                unidentifiedAccessHelper::getAccessFor,
                unidentified -> unidentified ? getOrCreateUnidentifiedMessagePipe() : getOrCreateMessagePipe(),
                () -> messageReceiver);
        this.groupHelper = new GroupHelper(this::getRecipientProfileKeyCredential,
                this::getRecipientProfile,
                account::getSelfAddress,
                groupsV2Operations,
                groupsV2Api,
                this::getGroupAuthForToday);
        this.avatarStore = new AvatarStore(pathConfig.getAvatarsPath());
        this.attachmentStore = new AttachmentStore(pathConfig.getAttachmentsPath());
    }

    public String getUsername() {
        return account.getUsername();
    }

    public SignalServiceAddress getSelfAddress() {
        return account.getSelfAddress();
    }

    private IdentityKeyPair getIdentityKeyPair() {
        return account.getSignalProtocolStore().getIdentityKeyPair();
    }

    public int getDeviceId() {
        return account.getDeviceId();
    }

    public static Manager init(
            String username, File settingsPath, ServiceEnvironment serviceEnvironment, String userAgent
    ) throws IOException, NotRegisteredException {
        PathConfig pathConfig = PathConfig.createDefault(settingsPath);

        if (!SignalAccount.userExists(pathConfig.getDataPath(), username)) {
            throw new NotRegisteredException();
        }

        SignalAccount account = SignalAccount.load(pathConfig.getDataPath(), username);

        if (!account.isRegistered()) {
            throw new NotRegisteredException();
        }

        final ServiceEnvironmentConfig serviceEnvironmentConfig = ServiceConfig.getServiceEnvironmentConfig(
                serviceEnvironment,
                userAgent);

        return new Manager(account, pathConfig, serviceEnvironmentConfig, userAgent);
    }

    public static List<String> getAllLocalUsernames(File settingsPath) {
        PathConfig pathConfig = PathConfig.createDefault(settingsPath);
        final File dataPath = pathConfig.getDataPath();
        final File[] files = dataPath.listFiles();

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
        if (accountManager.getPreKeysCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
            refreshPreKeys();
            account.save();
        }
        if (account.getUuid() == null) {
            account.setUuid(accountManager.getOwnUuid());
            account.save();
        }
        updateAccountAttributes();
    }

    /**
     * This is used for checking a set of phone numbers for registration on Signal
     *
     * @param numbers The set of phone number in question
     * @return A map of numbers to booleans. True if registered, false otherwise. Should never be null
     * @throws IOException if its unable to get the contacts to check if they're registered
     */
    public Map<String, Boolean> areUsersRegistered(Set<String> numbers) throws IOException {
        // Note "contactDetails" has no optionals. It only gives us info on users who are registered
        Map<String, UUID> contactDetails = getRegisteredUsers(numbers);

        Set<String> registeredUsers = contactDetails.keySet();

        return numbers.stream().collect(Collectors.toMap(x -> x, registeredUsers::contains));
    }

    public void updateAccountAttributes() throws IOException {
        accountManager.setAccountAttributes(null,
                account.getSignalProtocolStore().getLocalRegistrationId(),
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
     * @param name       if null, the previous name will be kept
     * @param about      if null, the previous about text will be kept
     * @param aboutEmoji if null, the previous about emoji will be kept
     * @param avatar     if avatar is null the image from the local avatar store is used (if present),
     *                   if it's Optional.absent(), the avatar will be removed
     */
    public void setProfile(String name, String about, String aboutEmoji, Optional<File> avatar) throws IOException {
        SignalProfileEntry profileEntry = account.getProfileStore().getProfileEntry(getSelfAddress());
        SignalProfile profile = profileEntry == null ? null : profileEntry.getProfile();
        SignalProfile newProfile = new SignalProfile(profile == null ? null : profile.getIdentityKey(),
                name != null ? name : profile == null || profile.getName() == null ? "" : profile.getName(),
                about != null ? about : profile == null || profile.getAbout() == null ? "" : profile.getAbout(),
                aboutEmoji != null
                        ? aboutEmoji
                        : profile == null || profile.getAboutEmoji() == null ? "" : profile.getAboutEmoji(),
                profile == null ? null : profile.getUnidentifiedAccess(),
                account.isUnrestrictedUnidentifiedAccess(),
                profile == null ? null : profile.getCapabilities());

        try (final StreamDetails streamDetails = avatar == null
                ? avatarStore.retrieveProfileAvatar(getSelfAddress())
                : avatar.isPresent() ? Utils.createStreamDetailsFromFile(avatar.get()) : null) {
            accountManager.setVersionedProfile(account.getUuid(),
                    account.getProfileKey(),
                    newProfile.getName(),
                    newProfile.getAbout(),
                    newProfile.getAboutEmoji(),
                    streamDetails);
        }

        if (avatar != null) {
            if (avatar.isPresent()) {
                avatarStore.storeProfileAvatar(getSelfAddress(),
                        outputStream -> IOUtils.copyFileToStream(avatar.get(), outputStream));
            } else {
                avatarStore.deleteProfileAvatar(getSelfAddress());
            }
        }
        account.getProfileStore()
                .updateProfile(getSelfAddress(),
                        account.getProfileKey(),
                        System.currentTimeMillis(),
                        newProfile,
                        profileEntry == null ? null : profileEntry.getProfileKeyCredential());

        try {
            sendSyncMessage(SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.LOCAL_PROFILE));
        } catch (UntrustedIdentityException ignored) {
        }
    }

    public void unregister() throws IOException {
        // When setting an empty GCM id, the Signal-Server also sets the fetchesMessages property to false.
        // If this is the master device, other users can't send messages to this number anymore.
        // If this is a linked device, other users can still send messages, but this device doesn't receive them anymore.
        accountManager.setGcmId(Optional.absent());
        accountManager.deleteAccount();

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
        DeviceLinkInfo info = DeviceLinkInfo.parseDeviceLinkUri(linkUri);

        addDevice(info.deviceIdentifier, info.deviceKey);
    }

    private void addDevice(String deviceIdentifier, ECPublicKey deviceKey) throws IOException, InvalidKeyException {
        IdentityKeyPair identityKeyPair = getIdentityKeyPair();
        String verificationCode = accountManager.getNewDeviceVerificationCode();

        accountManager.addDevice(deviceIdentifier,
                deviceKey,
                identityKeyPair,
                Optional.of(account.getProfileKey().serialize()),
                verificationCode);
        account.setMultiDevice(true);
        account.save();
    }

    public void setRegistrationLockPin(Optional<String> pin) throws IOException, UnauthenticatedResponseException {
        if (!account.isMasterDevice()) {
            throw new RuntimeException("Only master device can set a PIN");
        }
        if (pin.isPresent()) {
            final MasterKey masterKey = account.getPinMasterKey() != null
                    ? account.getPinMasterKey()
                    : KeyUtils.createMasterKey();

            pinHelper.setRegistrationLockPin(pin.get(), masterKey);

            account.setRegistrationLockPin(pin.get());
            account.setPinMasterKey(masterKey);
        } else {
            // Remove legacy registration lock
            accountManager.removeRegistrationLockV1();

            // Remove KBS Pin
            pinHelper.removeRegistrationLockPin();

            account.setRegistrationLockPin(null);
            account.setPinMasterKey(null);
        }
        account.save();
    }

    void refreshPreKeys() throws IOException {
        List<PreKeyRecord> oneTimePreKeys = generatePreKeys();
        final IdentityKeyPair identityKeyPair = getIdentityKeyPair();
        SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey(identityKeyPair);

        accountManager.setPreKeys(identityKeyPair.getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
    }

    private List<PreKeyRecord> generatePreKeys() {
        final int offset = account.getPreKeyIdOffset();

        List<PreKeyRecord> records = KeyUtils.generatePreKeyRecords(offset, ServiceConfig.PREKEY_BATCH_SIZE);
        account.addPreKeys(records);
        account.save();

        return records;
    }

    private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair) {
        final int signedPreKeyId = account.getNextSignedPreKeyId();

        SignedPreKeyRecord record = KeyUtils.generateSignedPreKeyRecord(identityKeyPair, signedPreKeyId);
        account.addSignedPreKey(record);
        account.save();

        return record;
    }

    private SignalServiceMessagePipe getOrCreateMessagePipe() {
        if (messagePipe == null) {
            messagePipe = messageReceiver.createMessagePipe();
        }
        return messagePipe;
    }

    private SignalServiceMessagePipe getOrCreateUnidentifiedMessagePipe() {
        if (unidentifiedMessagePipe == null) {
            unidentifiedMessagePipe = messageReceiver.createUnidentifiedMessagePipe();
        }
        return unidentifiedMessagePipe;
    }

    private SignalServiceMessageSender createMessageSender() {
        return new SignalServiceMessageSender(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                account.getUuid(),
                account.getUsername(),
                account.getPassword(),
                account.getDeviceId(),
                account.getSignalProtocolStore(),
                userAgent,
                account.isMultiDevice(),
                Optional.fromNullable(messagePipe),
                Optional.fromNullable(unidentifiedMessagePipe),
                Optional.absent(),
                clientZkProfileOperations,
                executor,
                ServiceConfig.MAX_ENVELOPE_SIZE,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY);
    }

    private SignalProfile getRecipientProfile(
            SignalServiceAddress address
    ) {
        return getRecipientProfile(address, false);
    }

    private SignalProfile getRecipientProfile(
            SignalServiceAddress address, boolean force
    ) {
        SignalProfileEntry profileEntry = account.getProfileStore().getProfileEntry(address);
        if (profileEntry == null) {
            return null;
        }
        long now = new Date().getTime();
        // Profiles are cached for 24h before retrieving them again
        if (!profileEntry.isRequestPending() && (
                force
                        || profileEntry.getProfile() == null
                        || now - profileEntry.getLastUpdateTimestamp() > 24 * 60 * 60 * 1000
        )) {
            profileEntry.setRequestPending(true);
            final SignalServiceProfile encryptedProfile;
            try {
                encryptedProfile = profileHelper.retrieveProfileSync(address, SignalServiceProfile.RequestType.PROFILE)
                        .getProfile();
            } catch (IOException e) {
                logger.warn("Failed to retrieve profile, ignoring: {}", e.getMessage());
                return null;
            } finally {
                profileEntry.setRequestPending(false);
            }

            final ProfileKey profileKey = profileEntry.getProfileKey();
            final SignalProfile profile = decryptProfileAndDownloadAvatar(address, profileKey, encryptedProfile);
            account.getProfileStore()
                    .updateProfile(address, profileKey, now, profile, profileEntry.getProfileKeyCredential());
            return profile;
        }
        return profileEntry.getProfile();
    }

    private ProfileKeyCredential getRecipientProfileKeyCredential(SignalServiceAddress address) {
        SignalProfileEntry profileEntry = account.getProfileStore().getProfileEntry(address);
        if (profileEntry == null) {
            return null;
        }
        if (profileEntry.getProfileKeyCredential() == null) {
            ProfileAndCredential profileAndCredential;
            try {
                profileAndCredential = profileHelper.retrieveProfileSync(address,
                        SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL);
            } catch (IOException e) {
                logger.warn("Failed to retrieve profile key credential, ignoring: {}", e.getMessage());
                return null;
            }

            long now = new Date().getTime();
            final ProfileKeyCredential profileKeyCredential = profileAndCredential.getProfileKeyCredential().orNull();
            final SignalProfile profile = decryptProfileAndDownloadAvatar(address,
                    profileEntry.getProfileKey(),
                    profileAndCredential.getProfile());
            account.getProfileStore()
                    .updateProfile(address, profileEntry.getProfileKey(), now, profile, profileKeyCredential);
            return profileKeyCredential;
        }
        return profileEntry.getProfileKeyCredential();
    }

    private SignalProfile decryptProfileAndDownloadAvatar(
            final SignalServiceAddress address, final ProfileKey profileKey, final SignalServiceProfile encryptedProfile
    ) {
        if (encryptedProfile.getAvatar() != null) {
            downloadProfileAvatar(address, encryptedProfile.getAvatar(), profileKey);
        }

        return ProfileUtils.decryptProfile(profileKey, encryptedProfile);
    }

    private Optional<SignalServiceAttachmentStream> createGroupAvatarAttachment(GroupId groupId) throws IOException {
        final StreamDetails streamDetails = avatarStore.retrieveGroupAvatar(groupId);
        if (streamDetails == null) {
            return Optional.absent();
        }

        return Optional.of(AttachmentUtils.createAttachment(streamDetails, Optional.absent()));
    }

    private Optional<SignalServiceAttachmentStream> createContactAvatarAttachment(SignalServiceAddress address) throws IOException {
        final StreamDetails streamDetails = avatarStore.retrieveContactAvatar(address);
        if (streamDetails == null) {
            return Optional.absent();
        }

        return Optional.of(AttachmentUtils.createAttachment(streamDetails, Optional.absent()));
    }

    private GroupInfo getGroupForSending(GroupId groupId) throws GroupNotFoundException, NotAGroupMemberException {
        GroupInfo g = getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        if (!g.isMember(account.getSelfAddress())) {
            throw new NotAGroupMemberException(groupId, g.getTitle());
        }
        return g;
    }

    private GroupInfo getGroupForUpdating(GroupId groupId) throws GroupNotFoundException, NotAGroupMemberException {
        GroupInfo g = getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        if (!g.isMember(account.getSelfAddress()) && !g.isPendingMember(account.getSelfAddress())) {
            throw new NotAGroupMemberException(groupId, g.getTitle());
        }
        return g;
    }

    public List<GroupInfo> getGroups() {
        return account.getGroupStore().getGroups();
    }

    public Pair<Long, List<SendMessageResult>> sendGroupMessage(
            String messageText, List<String> attachments, GroupId groupId
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(AttachmentUtils.getSignalServiceAttachments(attachments));
        }

        return sendGroupMessage(messageBuilder, groupId);
    }

    public Pair<Long, List<SendMessageResult>> sendGroupMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, GroupId groupId
    ) throws IOException, InvalidNumberException, NotAGroupMemberException, GroupNotFoundException {
        SignalServiceDataMessage.Reaction reaction = new SignalServiceDataMessage.Reaction(emoji,
                remove,
                canonicalizeAndResolveSignalServiceAddress(targetAuthor),
                targetSentTimestamp);
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .withReaction(reaction);

        return sendGroupMessage(messageBuilder, groupId);
    }

    public Pair<Long, List<SendMessageResult>> sendGroupMessage(
            SignalServiceDataMessage.Builder messageBuilder, GroupId groupId
    ) throws IOException, GroupNotFoundException, NotAGroupMemberException {
        final GroupInfo g = getGroupForSending(groupId);

        GroupUtils.setGroupContext(messageBuilder, g);
        messageBuilder.withExpiration(g.getMessageExpirationTime());

        return sendMessage(messageBuilder, g.getMembersWithout(account.getSelfAddress()));
    }

    public Pair<Long, List<SendMessageResult>> sendQuitGroupMessage(GroupId groupId) throws GroupNotFoundException, IOException, NotAGroupMemberException {
        SignalServiceDataMessage.Builder messageBuilder;

        final GroupInfo g = getGroupForUpdating(groupId);
        if (g instanceof GroupInfoV1) {
            GroupInfoV1 groupInfoV1 = (GroupInfoV1) g;
            SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.QUIT)
                    .withId(groupId.serialize())
                    .build();
            messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group);
            groupInfoV1.removeMember(account.getSelfAddress());
            account.getGroupStore().updateGroup(groupInfoV1);
        } else {
            final GroupInfoV2 groupInfoV2 = (GroupInfoV2) g;
            final Pair<DecryptedGroup, GroupChange> groupGroupChangePair = groupHelper.leaveGroup(groupInfoV2);
            groupInfoV2.setGroup(groupGroupChangePair.first());
            messageBuilder = getGroupUpdateMessageBuilder(groupInfoV2, groupGroupChangePair.second().toByteArray());
            account.getGroupStore().updateGroup(groupInfoV2);
        }

        return sendMessage(messageBuilder, g.getMembersWithout(account.getSelfAddress()));
    }

    public Pair<GroupId, List<SendMessageResult>> updateGroup(
            GroupId groupId, String name, List<String> members, File avatarFile
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, InvalidNumberException, NotAGroupMemberException {
        return sendUpdateGroupMessage(groupId,
                name,
                members == null ? null : getSignalServiceAddresses(members),
                avatarFile);
    }

    private Pair<GroupId, List<SendMessageResult>> sendUpdateGroupMessage(
            GroupId groupId, String name, Collection<SignalServiceAddress> members, File avatarFile
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException {
        GroupInfo g;
        SignalServiceDataMessage.Builder messageBuilder;
        if (groupId == null) {
            // Create new group
            GroupInfoV2 gv2 = groupHelper.createGroupV2(name == null ? "" : name,
                    members == null ? List.of() : members,
                    avatarFile);
            if (gv2 == null) {
                GroupInfoV1 gv1 = new GroupInfoV1(GroupIdV1.createRandom());
                gv1.addMembers(List.of(account.getSelfAddress()));
                updateGroupV1(gv1, name, members, avatarFile);
                messageBuilder = getGroupUpdateMessageBuilder(gv1);
                g = gv1;
            } else {
                if (avatarFile != null) {
                    avatarStore.storeGroupAvatar(gv2.getGroupId(),
                            outputStream -> IOUtils.copyFileToStream(avatarFile, outputStream));
                }
                messageBuilder = getGroupUpdateMessageBuilder(gv2, null);
                g = gv2;
            }
        } else {
            GroupInfo group = getGroupForUpdating(groupId);
            if (group instanceof GroupInfoV2) {
                final GroupInfoV2 groupInfoV2 = (GroupInfoV2) group;

                Pair<Long, List<SendMessageResult>> result = null;
                if (groupInfoV2.isPendingMember(getSelfAddress())) {
                    Pair<DecryptedGroup, GroupChange> groupGroupChangePair = groupHelper.acceptInvite(groupInfoV2);
                    result = sendUpdateGroupMessage(groupInfoV2,
                            groupGroupChangePair.first(),
                            groupGroupChangePair.second());
                }

                if (members != null) {
                    final Set<SignalServiceAddress> newMembers = new HashSet<>(members);
                    newMembers.removeAll(group.getMembers()
                            .stream()
                            .map(this::resolveSignalServiceAddress)
                            .collect(Collectors.toSet()));
                    if (newMembers.size() > 0) {
                        Pair<DecryptedGroup, GroupChange> groupGroupChangePair = groupHelper.updateGroupV2(groupInfoV2,
                                newMembers);
                        result = sendUpdateGroupMessage(groupInfoV2,
                                groupGroupChangePair.first(),
                                groupGroupChangePair.second());
                    }
                }
                if (result == null || name != null || avatarFile != null) {
                    Pair<DecryptedGroup, GroupChange> groupGroupChangePair = groupHelper.updateGroupV2(groupInfoV2,
                            name,
                            avatarFile);
                    if (avatarFile != null) {
                        avatarStore.storeGroupAvatar(groupInfoV2.getGroupId(),
                                outputStream -> IOUtils.copyFileToStream(avatarFile, outputStream));
                    }
                    result = sendUpdateGroupMessage(groupInfoV2,
                            groupGroupChangePair.first(),
                            groupGroupChangePair.second());
                }

                return new Pair<>(group.getGroupId(), result.second());
            } else {
                GroupInfoV1 gv1 = (GroupInfoV1) group;
                updateGroupV1(gv1, name, members, avatarFile);
                messageBuilder = getGroupUpdateMessageBuilder(gv1);
                g = gv1;
            }
        }

        account.getGroupStore().updateGroup(g);

        final Pair<Long, List<SendMessageResult>> result = sendMessage(messageBuilder,
                g.getMembersIncludingPendingWithout(account.getSelfAddress()));
        return new Pair<>(g.getGroupId(), result.second());
    }

    private void updateGroupV1(
            final GroupInfoV1 g,
            final String name,
            final Collection<SignalServiceAddress> members,
            final File avatarFile
    ) throws IOException {
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

            final Map<String, UUID> registeredUsers = getRegisteredUsers(newE164Members);
            if (registeredUsers.size() != newE164Members.size()) {
                // Some of the new members are not registered on Signal
                newE164Members.removeAll(registeredUsers.keySet());
                throw new IOException("Failed to add members "
                        + String.join(", ", newE164Members)
                        + " to group: Not registered on Signal");
            }

            g.addMembers(members);
        }

        if (avatarFile != null) {
            avatarStore.storeGroupAvatar(g.getGroupId(),
                    outputStream -> IOUtils.copyFileToStream(avatarFile, outputStream));
        }
    }

    public Pair<GroupId, List<SendMessageResult>> joinGroup(
            GroupInviteLinkUrl inviteLinkUrl
    ) throws IOException, GroupLinkNotActiveException {
        return sendJoinGroupMessage(inviteLinkUrl);
    }

    private Pair<GroupId, List<SendMessageResult>> sendJoinGroupMessage(
            GroupInviteLinkUrl inviteLinkUrl
    ) throws IOException, GroupLinkNotActiveException {
        final DecryptedGroupJoinInfo groupJoinInfo = groupHelper.getDecryptedGroupJoinInfo(inviteLinkUrl.getGroupMasterKey(),
                inviteLinkUrl.getPassword());
        final GroupChange groupChange = groupHelper.joinGroup(inviteLinkUrl.getGroupMasterKey(),
                inviteLinkUrl.getPassword(),
                groupJoinInfo);
        final GroupInfoV2 group = getOrMigrateGroup(inviteLinkUrl.getGroupMasterKey(),
                groupJoinInfo.getRevision() + 1,
                groupChange.toByteArray());

        if (group.getGroup() == null) {
            // Only requested member, can't send update to group members
            return new Pair<>(group.getGroupId(), List.of());
        }

        final Pair<Long, List<SendMessageResult>> result = sendUpdateGroupMessage(group, group.getGroup(), groupChange);

        return new Pair<>(group.getGroupId(), result.second());
    }

    private static int currentTimeDays() {
        return (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    }

    private GroupsV2AuthorizationString getGroupAuthForToday(
            final GroupSecretParams groupSecretParams
    ) throws IOException {
        final int today = currentTimeDays();
        // Returns credentials for the next 7 days
        final HashMap<Integer, AuthCredentialResponse> credentials = groupsV2Api.getCredentials(today);
        // TODO cache credentials until they expire
        AuthCredentialResponse authCredentialResponse = credentials.get(today);
        try {
            return groupsV2Api.getGroupsV2AuthorizationString(account.getUuid(),
                    today,
                    groupSecretParams,
                    authCredentialResponse);
        } catch (VerificationFailedException e) {
            throw new IOException(e);
        }
    }

    private Pair<Long, List<SendMessageResult>> sendUpdateGroupMessage(
            GroupInfoV2 group, DecryptedGroup newDecryptedGroup, GroupChange groupChange
    ) throws IOException {
        group.setGroup(newDecryptedGroup);
        final SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(group,
                groupChange.toByteArray());
        account.getGroupStore().updateGroup(group);
        return sendMessage(messageBuilder, group.getMembersIncludingPendingWithout(account.getSelfAddress()));
    }

    Pair<Long, List<SendMessageResult>> sendGroupInfoMessage(
            GroupIdV1 groupId, SignalServiceAddress recipient
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, AttachmentInvalidException {
        GroupInfoV1 g;
        GroupInfo group = getGroupForSending(groupId);
        if (!(group instanceof GroupInfoV1)) {
            throw new RuntimeException("Received an invalid group request for a v2 group!");
        }
        g = (GroupInfoV1) group;

        if (!g.isMember(recipient)) {
            throw new NotAGroupMemberException(groupId, g.name);
        }

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        // Send group message only to the recipient who requested it
        return sendMessage(messageBuilder, List.of(recipient));
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfoV1 g) throws AttachmentInvalidException {
        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                .withId(g.getGroupId().serialize())
                .withName(g.name)
                .withMembers(new ArrayList<>(g.getMembers()));

        try {
            final Optional<SignalServiceAttachmentStream> attachment = createGroupAvatarAttachment(g.getGroupId());
            if (attachment.isPresent()) {
                group.withAvatar(attachment.get());
            }
        } catch (IOException e) {
            throw new AttachmentInvalidException(g.getGroupId().toBase64(), e);
        }

        return SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build())
                .withExpiration(g.getMessageExpirationTime());
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfoV2 g, byte[] signedGroupChange) {
        SignalServiceGroupV2.Builder group = SignalServiceGroupV2.newBuilder(g.getMasterKey())
                .withRevision(g.getGroup().getRevision())
                .withSignedGroupChange(signedGroupChange);
        return SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build())
                .withExpiration(g.getMessageExpirationTime());
    }

    Pair<Long, List<SendMessageResult>> sendGroupInfoRequest(
            GroupIdV1 groupId, SignalServiceAddress recipient
    ) throws IOException {
        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.REQUEST_INFO)
                .withId(groupId.serialize());

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build());

        // Send group info request message to the recipient who sent us a message with this groupId
        return sendMessage(messageBuilder, List.of(recipient));
    }

    void sendReceipt(
            SignalServiceAddress remoteAddress, long messageId
    ) throws IOException, UntrustedIdentityException {
        SignalServiceReceiptMessage receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.DELIVERY,
                List.of(messageId),
                System.currentTimeMillis());

        createMessageSender().sendReceipt(remoteAddress,
                unidentifiedAccessHelper.getAccessFor(remoteAddress),
                receiptMessage);
    }

    public Pair<Long, List<SendMessageResult>> sendMessage(
            String messageText, List<String> attachments, List<String> recipients
    ) throws IOException, AttachmentInvalidException, InvalidNumberException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .withBody(messageText);
        if (attachments != null) {
            List<SignalServiceAttachment> attachmentStreams = AttachmentUtils.getSignalServiceAttachments(attachments);

            // Upload attachments here, so we only upload once even for multiple recipients
            SignalServiceMessageSender messageSender = createMessageSender();
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

    public Pair<Long, SendMessageResult> sendSelfMessage(
            String messageText, List<String> attachments
    ) throws IOException, AttachmentInvalidException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(AttachmentUtils.getSignalServiceAttachments(attachments));
        }
        return sendSelfMessage(messageBuilder);
    }

    public Pair<Long, List<SendMessageResult>> sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients
    ) throws IOException, InvalidNumberException {
        SignalServiceDataMessage.Reaction reaction = new SignalServiceDataMessage.Reaction(emoji,
                remove,
                canonicalizeAndResolveSignalServiceAddress(targetAuthor),
                targetSentTimestamp);
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .withReaction(reaction);
        return sendMessage(messageBuilder, getSignalServiceAddresses(recipients));
    }

    public Pair<Long, List<SendMessageResult>> sendEndSessionMessage(List<String> recipients) throws IOException, InvalidNumberException {
        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asEndSessionMessage();

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

    public void setGroupBlocked(final GroupId groupId, final boolean blocked) throws GroupNotFoundException {
        GroupInfo group = getGroup(groupId);
        if (group == null) {
            throw new GroupNotFoundException(groupId);
        }

        group.setBlocked(blocked);
        account.getGroupStore().updateGroup(group);
        account.save();
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
        sendMessage(messageBuilder, List.of(address));
    }

    /**
     * Change the expiration timer for a contact
     */
    public void setExpirationTimer(
            String number, int messageExpirationTimer
    ) throws IOException, InvalidNumberException {
        SignalServiceAddress address = canonicalizeAndResolveSignalServiceAddress(number);
        setExpirationTimer(address, messageExpirationTimer);
    }

    /**
     * Change the expiration timer for a group
     */
    public void setExpirationTimer(GroupId groupId, int messageExpirationTimer) {
        GroupInfo g = getGroup(groupId);
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
    public String uploadStickerPack(File path) throws IOException, StickerPackInvalidException {
        SignalServiceStickerManifestUpload manifest = StickerUtils.getSignalServiceStickerManifestUpload(path);

        SignalServiceMessageSender messageSender = createMessageSender();

        byte[] packKey = KeyUtils.createStickerUploadKey();
        String packId = messageSender.uploadStickerManifest(manifest, packKey);

        Sticker sticker = new Sticker(Hex.fromStringCondensed(packId), packKey);
        account.getStickerStore().updateSticker(sticker);
        account.save();

        try {
            return new URI("https",
                    "signal.art",
                    "/addstickers/",
                    "pack_id=" + URLEncoder.encode(packId, StandardCharsets.UTF_8) + "&pack_key=" + URLEncoder.encode(
                            Hex.toStringCondensed(packKey),
                            StandardCharsets.UTF_8)).toString();
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    void requestSyncGroups() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS)
                .build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            throw new AssertionError(e);
        }
    }

    void requestSyncContacts() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS)
                .build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            throw new AssertionError(e);
        }
    }

    void requestSyncBlocked() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.BLOCKED)
                .build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            throw new AssertionError(e);
        }
    }

    void requestSyncConfiguration() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.CONFIGURATION)
                .build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            throw new AssertionError(e);
        }
    }

    void requestSyncKeys() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.KEYS)
                .build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            throw new AssertionError(e);
        }
    }

    private byte[] getSenderCertificate() {
        // TODO support UUID capable sender certificates
        // byte[] certificate = accountManager.getSenderCertificateForPhoneNumberPrivacy();
        byte[] certificate;
        try {
            certificate = accountManager.getSenderCertificate();
        } catch (IOException e) {
            logger.warn("Failed to get sender certificate, ignoring: {}", e.getMessage());
            return null;
        }
        // TODO cache for a day
        return certificate;
    }

    private void sendSyncMessage(SignalServiceSyncMessage message) throws IOException, UntrustedIdentityException {
        SignalServiceMessageSender messageSender = createMessageSender();
        try {
            messageSender.sendMessage(message, unidentifiedAccessHelper.getAccessForSync());
        } catch (UntrustedIdentityException e) {
            account.getSignalProtocolStore()
                    .saveIdentity(resolveSignalServiceAddress(e.getIdentifier()),
                            e.getIdentityKey(),
                            TrustLevel.UNTRUSTED);
            throw e;
        }
    }

    private Collection<SignalServiceAddress> getSignalServiceAddresses(Collection<String> numbers) throws InvalidNumberException {
        final Set<SignalServiceAddress> signalServiceAddresses = new HashSet<>(numbers.size());
        final Set<SignalServiceAddress> addressesMissingUuid = new HashSet<>();

        for (String number : numbers) {
            final SignalServiceAddress resolvedAddress = canonicalizeAndResolveSignalServiceAddress(number);
            if (resolvedAddress.getUuid().isPresent()) {
                signalServiceAddresses.add(resolvedAddress);
            } else {
                addressesMissingUuid.add(resolvedAddress);
            }
        }

        final Set<String> numbersMissingUuid = addressesMissingUuid.stream()
                .map(a -> a.getNumber().get())
                .collect(Collectors.toSet());
        Map<String, UUID> registeredUsers;
        try {
            registeredUsers = getRegisteredUsers(numbersMissingUuid);
        } catch (IOException e) {
            logger.warn("Failed to resolve uuids from server, ignoring: {}", e.getMessage());
            registeredUsers = Map.of();
        }

        for (SignalServiceAddress address : addressesMissingUuid) {
            final String number = address.getNumber().get();
            if (registeredUsers.containsKey(number)) {
                final SignalServiceAddress newAddress = resolveSignalServiceAddress(new SignalServiceAddress(
                        registeredUsers.get(number),
                        number));
                signalServiceAddresses.add(newAddress);
            } else {
                signalServiceAddresses.add(address);
            }
        }

        return signalServiceAddresses;
    }

    private Map<String, UUID> getRegisteredUsers(final Set<String> numbersMissingUuid) throws IOException {
        try {
            return accountManager.getRegisteredUsers(ServiceConfig.getIasKeyStore(),
                    numbersMissingUuid,
                    serviceEnvironmentConfig.getCdsMrenclave());
        } catch (Quote.InvalidQuoteFormatException | UnauthenticatedQuoteException | SignatureException | UnauthenticatedResponseException | InvalidKeyException e) {
            throw new IOException(e);
        }
    }

    private Pair<Long, List<SendMessageResult>> sendMessage(
            SignalServiceDataMessage.Builder messageBuilder, Collection<SignalServiceAddress> recipients
    ) throws IOException {
        recipients = recipients.stream().map(this::resolveSignalServiceAddress).collect(Collectors.toSet());
        final long timestamp = System.currentTimeMillis();
        messageBuilder.withTimestamp(timestamp);
        getOrCreateMessagePipe();
        getOrCreateUnidentifiedMessagePipe();
        SignalServiceDataMessage message = null;
        try {
            message = messageBuilder.build();
            if (message.getGroupContext().isPresent()) {
                try {
                    SignalServiceMessageSender messageSender = createMessageSender();
                    final boolean isRecipientUpdate = false;
                    List<SendMessageResult> result = messageSender.sendMessage(new ArrayList<>(recipients),
                            unidentifiedAccessHelper.getAccessFor(recipients),
                            isRecipientUpdate,
                            message);
                    for (SendMessageResult r : result) {
                        if (r.getIdentityFailure() != null) {
                            account.getSignalProtocolStore()
                                    .saveIdentity(r.getAddress(),
                                            r.getIdentityFailure().getIdentityKey(),
                                            TrustLevel.UNTRUSTED);
                        }
                    }
                    return new Pair<>(timestamp, result);
                } catch (UntrustedIdentityException e) {
                    account.getSignalProtocolStore()
                            .saveIdentity(resolveSignalServiceAddress(e.getIdentifier()),
                                    e.getIdentityKey(),
                                    TrustLevel.UNTRUSTED);
                    return new Pair<>(timestamp, List.of());
                }
            } else {
                // Send to all individually, so sync messages are sent correctly
                messageBuilder.withProfileKey(account.getProfileKey().serialize());
                List<SendMessageResult> results = new ArrayList<>(recipients.size());
                for (SignalServiceAddress address : recipients) {
                    final ContactInfo contact = account.getContactStore().getContact(address);
                    final int expirationTime = contact != null ? contact.messageExpirationTime : 0;
                    messageBuilder.withExpiration(expirationTime);
                    message = messageBuilder.build();
                    results.add(sendMessage(address, message));
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

    private Pair<Long, SendMessageResult> sendSelfMessage(
            SignalServiceDataMessage.Builder messageBuilder
    ) throws IOException {
        final long timestamp = System.currentTimeMillis();
        messageBuilder.withTimestamp(timestamp);
        getOrCreateMessagePipe();
        getOrCreateUnidentifiedMessagePipe();
        try {
            final SignalServiceAddress address = getSelfAddress();

            final ContactInfo contact = account.getContactStore().getContact(address);
            final int expirationTime = contact != null ? contact.messageExpirationTime : 0;
            messageBuilder.withExpiration(expirationTime);

            SignalServiceDataMessage message = messageBuilder.build();
            final SendMessageResult result = sendSelfMessage(message);
            return new Pair<>(timestamp, result);
        } finally {
            account.save();
        }
    }

    private SendMessageResult sendSelfMessage(SignalServiceDataMessage message) throws IOException {
        SignalServiceMessageSender messageSender = createMessageSender();

        SignalServiceAddress recipient = account.getSelfAddress();

        final Optional<UnidentifiedAccessPair> unidentifiedAccess = unidentifiedAccessHelper.getAccessFor(recipient);
        SentTranscriptMessage transcript = new SentTranscriptMessage(Optional.of(recipient),
                message.getTimestamp(),
                message,
                message.getExpiresInSeconds(),
                Map.of(recipient, unidentifiedAccess.isPresent()),
                false);
        SignalServiceSyncMessage syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);

        try {
            long startTime = System.currentTimeMillis();
            messageSender.sendMessage(syncMessage, unidentifiedAccess);
            return SendMessageResult.success(recipient,
                    unidentifiedAccess.isPresent(),
                    false,
                    System.currentTimeMillis() - startTime);
        } catch (UntrustedIdentityException e) {
            account.getSignalProtocolStore()
                    .saveIdentity(resolveSignalServiceAddress(e.getIdentifier()),
                            e.getIdentityKey(),
                            TrustLevel.UNTRUSTED);
            return SendMessageResult.identityFailure(recipient, e.getIdentityKey());
        }
    }

    private SendMessageResult sendMessage(
            SignalServiceAddress address, SignalServiceDataMessage message
    ) throws IOException {
        SignalServiceMessageSender messageSender = createMessageSender();

        try {
            return messageSender.sendMessage(address, unidentifiedAccessHelper.getAccessFor(address), message);
        } catch (UntrustedIdentityException e) {
            account.getSignalProtocolStore()
                    .saveIdentity(resolveSignalServiceAddress(e.getIdentifier()),
                            e.getIdentityKey(),
                            TrustLevel.UNTRUSTED);
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        }
    }

    private SignalServiceContent decryptMessage(SignalServiceEnvelope envelope) throws InvalidMetadataMessageException, ProtocolInvalidMessageException, ProtocolDuplicateMessageException, ProtocolLegacyMessageException, ProtocolInvalidKeyIdException, InvalidMetadataVersionException, ProtocolInvalidVersionException, ProtocolNoSessionException, ProtocolInvalidKeyException, SelfSendException, UnsupportedDataMessageException, org.whispersystems.libsignal.UntrustedIdentityException {
        SignalServiceCipher cipher = new SignalServiceCipher(account.getSelfAddress(),
                account.getSignalProtocolStore(),
                certificateValidator);
        try {
            return cipher.decrypt(envelope);
        } catch (ProtocolUntrustedIdentityException e) {
            if (e.getCause() instanceof org.whispersystems.libsignal.UntrustedIdentityException) {
                org.whispersystems.libsignal.UntrustedIdentityException identityException = (org.whispersystems.libsignal.UntrustedIdentityException) e
                        .getCause();
                final IdentityKey untrustedIdentity = identityException.getUntrustedIdentity();
                if (untrustedIdentity != null) {
                    account.getSignalProtocolStore()
                            .saveIdentity(resolveSignalServiceAddress(identityException.getName()),
                                    untrustedIdentity,
                                    TrustLevel.UNTRUSTED);
                }
                throw identityException;
            }
            throw new AssertionError(e);
        }
    }

    private void handleEndSession(SignalServiceAddress source) {
        account.getSignalProtocolStore().deleteAllSessions(source);
    }

    private List<HandleAction> handleSignalServiceDataMessage(
            SignalServiceDataMessage message,
            boolean isSync,
            SignalServiceAddress source,
            SignalServiceAddress destination,
            boolean ignoreAttachments
    ) {
        List<HandleAction> actions = new ArrayList<>();
        if (message.getGroupContext().isPresent()) {
            if (message.getGroupContext().get().getGroupV1().isPresent()) {
                SignalServiceGroup groupInfo = message.getGroupContext().get().getGroupV1().get();
                GroupIdV1 groupId = GroupId.v1(groupInfo.getGroupId());
                GroupInfo group = getGroup(groupId);
                if (group == null || group instanceof GroupInfoV1) {
                    GroupInfoV1 groupV1 = (GroupInfoV1) group;
                    switch (groupInfo.getType()) {
                        case UPDATE: {
                            if (groupV1 == null) {
                                groupV1 = new GroupInfoV1(groupId);
                            }

                            if (groupInfo.getAvatar().isPresent()) {
                                SignalServiceAttachment avatar = groupInfo.getAvatar().get();
                                downloadGroupAvatar(avatar, groupV1.getGroupId());
                            }

                            if (groupInfo.getName().isPresent()) {
                                groupV1.name = groupInfo.getName().get();
                            }

                            if (groupInfo.getMembers().isPresent()) {
                                groupV1.addMembers(groupInfo.getMembers()
                                        .get()
                                        .stream()
                                        .map(this::resolveSignalServiceAddress)
                                        .collect(Collectors.toSet()));
                            }

                            account.getGroupStore().updateGroup(groupV1);
                            break;
                        }
                        case DELIVER:
                            if (groupV1 == null && !isSync) {
                                actions.add(new SendGroupInfoRequestAction(source, groupId));
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
                                actions.add(new SendGroupInfoAction(source, groupV1.getGroupId()));
                            }
                            break;
                    }
                } else {
                    // Received a group v1 message for a v2 group
                }
            }
            if (message.getGroupContext().get().getGroupV2().isPresent()) {
                final SignalServiceGroupV2 groupContext = message.getGroupContext().get().getGroupV2().get();
                final GroupMasterKey groupMasterKey = groupContext.getMasterKey();

                getOrMigrateGroup(groupMasterKey,
                        groupContext.getRevision(),
                        groupContext.hasSignedGroupChange() ? groupContext.getSignedGroupChange() : null);
            }
        }

        final SignalServiceAddress conversationPartnerAddress = isSync ? destination : source;
        if (conversationPartnerAddress != null && message.isEndSession()) {
            handleEndSession(conversationPartnerAddress);
        }
        if (message.isExpirationUpdate() || message.getBody().isPresent()) {
            if (message.getGroupContext().isPresent()) {
                if (message.getGroupContext().get().getGroupV1().isPresent()) {
                    SignalServiceGroup groupInfo = message.getGroupContext().get().getGroupV1().get();
                    GroupInfoV1 group = account.getGroupStore().getOrCreateGroupV1(GroupId.v1(groupInfo.getGroupId()));
                    if (group != null) {
                        if (group.messageExpirationTime != message.getExpiresInSeconds()) {
                            group.messageExpirationTime = message.getExpiresInSeconds();
                            account.getGroupStore().updateGroup(group);
                        }
                    }
                } else if (message.getGroupContext().get().getGroupV2().isPresent()) {
                    // disappearing message timer already stored in the DecryptedGroup
                }
            } else if (conversationPartnerAddress != null) {
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
        if (!ignoreAttachments) {
            if (message.getAttachments().isPresent()) {
                for (SignalServiceAttachment attachment : message.getAttachments().get()) {
                    downloadAttachment(attachment);
                }
            }
            if (message.getSharedContacts().isPresent()) {
                for (SharedContact contact : message.getSharedContacts().get()) {
                    if (contact.getAvatar().isPresent()) {
                        downloadAttachment(contact.getAvatar().get().getAttachment());
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
                if (preview.getImage().isPresent()) {
                    downloadAttachment(preview.getImage().get());
                }
            }
        }
        if (message.getQuote().isPresent()) {
            final SignalServiceDataMessage.Quote quote = message.getQuote().get();

            for (SignalServiceDataMessage.Quote.QuotedAttachment quotedAttachment : quote.getAttachments()) {
                final SignalServiceAttachment thumbnail = quotedAttachment.getThumbnail();
                if (thumbnail != null) {
                    downloadAttachment(thumbnail);
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

    private GroupInfoV2 getOrMigrateGroup(
            final GroupMasterKey groupMasterKey, final int revision, final byte[] signedGroupChange
    ) {
        final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

        GroupIdV2 groupId = GroupUtils.getGroupIdV2(groupSecretParams);
        GroupInfo groupInfo = getGroup(groupId);
        final GroupInfoV2 groupInfoV2;
        if (groupInfo instanceof GroupInfoV1) {
            // Received a v2 group message for a v1 group, we need to locally migrate the group
            account.getGroupStore().deleteGroup(groupInfo.getGroupId());
            groupInfoV2 = new GroupInfoV2(groupId, groupMasterKey);
            logger.info("Locally migrated group {} to group v2, id: {}",
                    groupInfo.getGroupId().toBase64(),
                    groupInfoV2.getGroupId().toBase64());
        } else if (groupInfo instanceof GroupInfoV2) {
            groupInfoV2 = (GroupInfoV2) groupInfo;
        } else {
            groupInfoV2 = new GroupInfoV2(groupId, groupMasterKey);
        }

        if (groupInfoV2.getGroup() == null || groupInfoV2.getGroup().getRevision() < revision) {
            DecryptedGroup group = null;
            if (signedGroupChange != null
                    && groupInfoV2.getGroup() != null
                    && groupInfoV2.getGroup().getRevision() + 1 == revision) {
                group = groupHelper.getUpdatedDecryptedGroup(groupInfoV2.getGroup(), signedGroupChange, groupMasterKey);
            }
            if (group == null) {
                group = groupHelper.getDecryptedGroup(groupSecretParams);
            }
            if (group != null) {
                storeProfileKeysFromMembers(group);
                final String avatar = group.getAvatar();
                if (avatar != null && !avatar.isEmpty()) {
                    downloadGroupAvatar(groupId, groupSecretParams, avatar);
                }
            }
            groupInfoV2.setGroup(group);
            account.getGroupStore().updateGroup(groupInfoV2);
        }

        return groupInfoV2;
    }

    private void storeProfileKeysFromMembers(final DecryptedGroup group) {
        for (DecryptedMember member : group.getMembersList()) {
            final SignalServiceAddress address = resolveSignalServiceAddress(new SignalServiceAddress(UuidUtil.parseOrThrow(
                    member.getUuid().toByteArray()), null));
            try {
                account.getProfileStore()
                        .storeProfileKey(address, new ProfileKey(member.getProfileKey().toByteArray()));
            } catch (InvalidInputException ignored) {
            }
        }
    }

    private void retryFailedReceivedMessages(ReceiveMessageHandler handler, boolean ignoreAttachments) {
        for (CachedMessage cachedMessage : account.getMessageCache().getCachedMessages()) {
            retryFailedReceivedMessage(handler, ignoreAttachments, cachedMessage);
        }
    }

    private void retryFailedReceivedMessage(
            final ReceiveMessageHandler handler, final boolean ignoreAttachments, final CachedMessage cachedMessage
    ) {
        SignalServiceEnvelope envelope = cachedMessage.loadEnvelope();
        if (envelope == null) {
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
                cachedMessage.delete();
                return;
            }
            List<HandleAction> actions = handleMessage(envelope, content, ignoreAttachments);
            for (HandleAction action : actions) {
                try {
                    action.execute(this);
                } catch (Throwable e) {
                    logger.warn("Message action failed.", e);
                }
            }
        }
        account.save();
        handler.handleMessage(envelope, content, null);
        cachedMessage.delete();
    }

    public void receiveMessages(
            long timeout,
            TimeUnit unit,
            boolean returnOnTimeout,
            boolean ignoreAttachments,
            ReceiveMessageHandler handler
    ) throws IOException {
        retryFailedReceivedMessages(handler, ignoreAttachments);

        Set<HandleAction> queuedActions = null;

        final SignalServiceMessagePipe messagePipe = getOrCreateMessagePipe();

        boolean hasCaughtUpWithOldMessages = false;

        while (true) {
            SignalServiceEnvelope envelope;
            SignalServiceContent content = null;
            Exception exception = null;
            final CachedMessage[] cachedMessage = {null};
            try {
                Optional<SignalServiceEnvelope> result = messagePipe.readOrEmpty(timeout, unit, envelope1 -> {
                    // store message on disk, before acknowledging receipt to the server
                    cachedMessage[0] = account.getMessageCache().cacheMessage(envelope1);
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
                                logger.warn("Message action failed.", e);
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
                if (returnOnTimeout) return;
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
                            logger.warn("Message action failed.", e);
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
            if (isMessageBlocked(envelope, content)) {
                logger.info("Ignoring a message from blocked user/group: {}", envelope.getTimestamp());
            } else if (isNotAGroupMember(envelope, content)) {
                logger.info("Ignoring a message from a non group member: {}", envelope.getTimestamp());
            } else {
                handler.handleMessage(envelope, content, exception);
            }
            if (!(exception instanceof org.whispersystems.libsignal.UntrustedIdentityException)) {
                if (cachedMessage[0] != null) {
                    cachedMessage[0].delete();
                }
            }
        }
    }

    private boolean isMessageBlocked(
            SignalServiceEnvelope envelope, SignalServiceContent content
    ) {
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
            if (message.getGroupContext().isPresent()) {
                GroupId groupId = GroupUtils.getGroupId(message.getGroupContext().get());
                GroupInfo group = getGroup(groupId);
                if (group != null && group.isBlocked()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNotAGroupMember(
            SignalServiceEnvelope envelope, SignalServiceContent content
    ) {
        SignalServiceAddress source;
        if (!envelope.isUnidentifiedSender() && envelope.hasSource()) {
            source = envelope.getSourceAddress();
        } else if (content != null) {
            source = content.getSender();
        } else {
            return false;
        }

        if (content != null && content.getDataMessage().isPresent()) {
            SignalServiceDataMessage message = content.getDataMessage().get();
            if (message.getGroupContext().isPresent()) {
                if (message.getGroupContext().get().getGroupV1().isPresent()) {
                    SignalServiceGroup groupInfo = message.getGroupContext().get().getGroupV1().get();
                    if (groupInfo.getType() == SignalServiceGroup.Type.QUIT) {
                        return false;
                    }
                }
                GroupId groupId = GroupUtils.getGroupId(message.getGroupContext().get());
                GroupInfo group = getGroup(groupId);
                if (group != null && !group.isMember(source)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<HandleAction> handleMessage(
            SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments
    ) {
        List<HandleAction> actions = new ArrayList<>();
        if (content != null) {
            final SignalServiceAddress sender;
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

                actions.addAll(handleSignalServiceDataMessage(message,
                        false,
                        sender,
                        account.getSelfAddress(),
                        ignoreAttachments));
            }
            if (content.getSyncMessage().isPresent()) {
                account.setMultiDevice(true);
                SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();
                if (syncMessage.getSent().isPresent()) {
                    SentTranscriptMessage message = syncMessage.getSent().get();
                    final SignalServiceAddress destination = message.getDestination().orNull();
                    actions.addAll(handleSignalServiceDataMessage(message.getMessage(),
                            true,
                            sender,
                            destination,
                            ignoreAttachments));
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
                        final SignalServiceAttachment groupsMessage = syncMessage.getGroups().get();
                        try (InputStream attachmentAsStream = retrieveAttachmentAsStream(groupsMessage.asPointer(),
                                tmpFile)) {
                            DeviceGroupsInputStream s = new DeviceGroupsInputStream(attachmentAsStream);
                            DeviceGroup g;
                            while ((g = s.read()) != null) {
                                GroupInfoV1 syncGroup = account.getGroupStore()
                                        .getOrCreateGroupV1(GroupId.v1(g.getId()));
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
                                        syncGroup.addMembers(List.of(account.getSelfAddress()));
                                    }
                                    syncGroup.blocked = g.isBlocked();
                                    if (g.getColor().isPresent()) {
                                        syncGroup.color = g.getColor().get();
                                    }

                                    if (g.getAvatar().isPresent()) {
                                        downloadGroupAvatar(g.getAvatar().get(), syncGroup.getGroupId());
                                    }
                                    syncGroup.inboxPosition = g.getInboxPosition().orNull();
                                    syncGroup.archived = g.isArchived();
                                    account.getGroupStore().updateGroup(syncGroup);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to handle received sync groups “{}”, ignoring: {}",
                                tmpFile,
                                e.getMessage());
                    } finally {
                        if (tmpFile != null) {
                            try {
                                Files.delete(tmpFile.toPath());
                            } catch (IOException e) {
                                logger.warn("Failed to delete received groups temp file “{}”, ignoring: {}",
                                        tmpFile,
                                        e.getMessage());
                            }
                        }
                    }
                }
                if (syncMessage.getBlockedList().isPresent()) {
                    final BlockedListMessage blockedListMessage = syncMessage.getBlockedList().get();
                    for (SignalServiceAddress address : blockedListMessage.getAddresses()) {
                        setContactBlocked(resolveSignalServiceAddress(address), true);
                    }
                    for (GroupId groupId : blockedListMessage.getGroupIds()
                            .stream()
                            .map(GroupId::unknownVersion)
                            .collect(Collectors.toSet())) {
                        try {
                            setGroupBlocked(groupId, true);
                        } catch (GroupNotFoundException e) {
                            logger.warn("BlockedListMessage contained groupID that was not found in GroupStore: {}",
                                    groupId.toBase64());
                        }
                    }
                }
                if (syncMessage.getContacts().isPresent()) {
                    File tmpFile = null;
                    try {
                        tmpFile = IOUtils.createTempFile();
                        final ContactsMessage contactsMessage = syncMessage.getContacts().get();
                        try (InputStream attachmentAsStream = retrieveAttachmentAsStream(contactsMessage.getContactsStream()
                                .asPointer(), tmpFile)) {
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
                                    account.getSignalProtocolStore()
                                            .setIdentityTrustLevel(verifiedMessage.getDestination(),
                                                    verifiedMessage.getIdentityKey(),
                                                    TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
                                }
                                if (c.getExpirationTimer().isPresent()) {
                                    contact.messageExpirationTime = c.getExpirationTimer().get();
                                }
                                contact.blocked = c.isBlocked();
                                contact.inboxPosition = c.getInboxPosition().orNull();
                                contact.archived = c.isArchived();
                                account.getContactStore().updateContact(contact);

                                if (c.getAvatar().isPresent()) {
                                    downloadContactAvatar(c.getAvatar().get(), contact.getAddress());
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to handle received sync contacts “{}”, ignoring: {}",
                                tmpFile,
                                e.getMessage());
                    } finally {
                        if (tmpFile != null) {
                            try {
                                Files.delete(tmpFile.toPath());
                            } catch (IOException e) {
                                logger.warn("Failed to delete received contacts temp file “{}”, ignoring: {}",
                                        tmpFile,
                                        e.getMessage());
                            }
                        }
                    }
                }
                if (syncMessage.getVerified().isPresent()) {
                    final VerifiedMessage verifiedMessage = syncMessage.getVerified().get();
                    account.getSignalProtocolStore()
                            .setIdentityTrustLevel(resolveSignalServiceAddress(verifiedMessage.getDestination()),
                                    verifiedMessage.getIdentityKey(),
                                    TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
                }
                if (syncMessage.getStickerPackOperations().isPresent()) {
                    final List<StickerPackOperationMessage> stickerPackOperationMessages = syncMessage.getStickerPackOperations()
                            .get();
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
                        sticker.setInstalled(!m.getType().isPresent()
                                || m.getType().get() == StickerPackOperationMessage.Type.INSTALL);
                        account.getStickerStore().updateSticker(sticker);
                    }
                }
                if (syncMessage.getFetchType().isPresent()) {
                    switch (syncMessage.getFetchType().get()) {
                        case LOCAL_PROFILE:
                            getRecipientProfile(getSelfAddress(), true);
                        case STORAGE_MANIFEST:
                            // TODO
                    }
                }
                if (syncMessage.getKeys().isPresent()) {
                    final KeysMessage keysMessage = syncMessage.getKeys().get();
                    if (keysMessage.getStorageService().isPresent()) {
                        final StorageKey storageKey = keysMessage.getStorageService().get();
                        account.setStorageKey(storageKey);
                    }
                }
                if (syncMessage.getConfiguration().isPresent()) {
                    // TODO
                }
            }
        }
        return actions;
    }

    private void downloadContactAvatar(SignalServiceAttachment avatar, SignalServiceAddress address) {
        try {
            avatarStore.storeContactAvatar(address, outputStream -> retrieveAttachment(avatar, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download avatar for contact {}, ignoring: {}", address, e.getMessage());
        }
    }

    private void downloadGroupAvatar(SignalServiceAttachment avatar, GroupId groupId) {
        try {
            avatarStore.storeGroupAvatar(groupId, outputStream -> retrieveAttachment(avatar, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download avatar for group {}, ignoring: {}", groupId.toBase64(), e.getMessage());
        }
    }

    private void downloadGroupAvatar(GroupId groupId, GroupSecretParams groupSecretParams, String cdnKey) {
        try {
            avatarStore.storeGroupAvatar(groupId,
                    outputStream -> retrieveGroupV2Avatar(groupSecretParams, cdnKey, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download avatar for group {}, ignoring: {}", groupId.toBase64(), e.getMessage());
        }
    }

    private void downloadProfileAvatar(
            SignalServiceAddress address, String avatarPath, ProfileKey profileKey
    ) {
        try {
            avatarStore.storeProfileAvatar(address,
                    outputStream -> retrieveProfileAvatar(avatarPath, profileKey, outputStream));
        } catch (Throwable e) {
            logger.warn("Failed to download profile avatar, ignoring: {}", e.getMessage());
        }
    }

    public File getAttachmentFile(SignalServiceAttachmentRemoteId attachmentId) {
        return attachmentStore.getAttachmentFile(attachmentId);
    }

    private void downloadAttachment(final SignalServiceAttachment attachment) {
        if (!attachment.isPointer()) {
            logger.warn("Invalid state, can't store an attachment stream.");
        }

        SignalServiceAttachmentPointer pointer = attachment.asPointer();
        if (pointer.getPreview().isPresent()) {
            final byte[] preview = pointer.getPreview().get();
            try {
                attachmentStore.storeAttachmentPreview(pointer.getRemoteId(),
                        outputStream -> outputStream.write(preview, 0, preview.length));
            } catch (IOException e) {
                logger.warn("Failed to download attachment preview, ignoring: {}", e.getMessage());
            }
        }

        try {
            attachmentStore.storeAttachment(pointer.getRemoteId(),
                    outputStream -> retrieveAttachmentPointer(pointer, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download attachment ({}), ignoring: {}", pointer.getRemoteId(), e.getMessage());
        }
    }

    private void retrieveGroupV2Avatar(
            GroupSecretParams groupSecretParams, String cdnKey, OutputStream outputStream
    ) throws IOException {
        GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(groupSecretParams);

        File tmpFile = IOUtils.createTempFile();
        try (InputStream input = messageReceiver.retrieveGroupsV2ProfileAvatar(cdnKey,
                tmpFile,
                ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE)) {
            byte[] encryptedData = IOUtils.readFully(input);

            byte[] decryptedData = groupOperations.decryptAvatar(encryptedData);
            outputStream.write(decryptedData);
        } finally {
            try {
                Files.delete(tmpFile.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete received group avatar temp file “{}”, ignoring: {}",
                        tmpFile,
                        e.getMessage());
            }
        }
    }

    private void retrieveProfileAvatar(
            String avatarPath, ProfileKey profileKey, OutputStream outputStream
    ) throws IOException {
        File tmpFile = IOUtils.createTempFile();
        try (InputStream input = messageReceiver.retrieveProfileAvatar(avatarPath,
                tmpFile,
                profileKey,
                ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE)) {
            // Use larger buffer size to prevent AssertionError: Need: 12272 but only have: 8192 ...
            IOUtils.copyStream(input, outputStream, (int) ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE);
        } finally {
            try {
                Files.delete(tmpFile.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete received profile avatar temp file “{}”, ignoring: {}",
                        tmpFile,
                        e.getMessage());
            }
        }
    }

    private void retrieveAttachment(
            final SignalServiceAttachment attachment, final OutputStream outputStream
    ) throws IOException {
        if (attachment.isPointer()) {
            SignalServiceAttachmentPointer pointer = attachment.asPointer();
            retrieveAttachmentPointer(pointer, outputStream);
        } else {
            SignalServiceAttachmentStream stream = attachment.asStream();
            IOUtils.copyStream(stream.getInputStream(), outputStream);
        }
    }

    private void retrieveAttachmentPointer(
            SignalServiceAttachmentPointer pointer, OutputStream outputStream
    ) throws IOException {
        File tmpFile = IOUtils.createTempFile();
        try (InputStream input = retrieveAttachmentAsStream(pointer, tmpFile)) {
            IOUtils.copyStream(input, outputStream);
        } catch (MissingConfigurationException | InvalidMessageException e) {
            throw new IOException(e);
        } finally {
            try {
                Files.delete(tmpFile.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete received attachment temp file “{}”, ignoring: {}",
                        tmpFile,
                        e.getMessage());
            }
        }
    }

    private InputStream retrieveAttachmentAsStream(
            SignalServiceAttachmentPointer pointer, File tmpFile
    ) throws IOException, InvalidMessageException, MissingConfigurationException {
        return messageReceiver.retrieveAttachment(pointer, tmpFile, ServiceConfig.MAX_ATTACHMENT_SIZE);
    }

    void sendGroups() throws IOException, UntrustedIdentityException {
        File groupsFile = IOUtils.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(groupsFile)) {
                DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(fos);
                for (GroupInfo record : getGroups()) {
                    if (record instanceof GroupInfoV1) {
                        GroupInfoV1 groupInfo = (GroupInfoV1) record;
                        out.write(new DeviceGroup(groupInfo.getGroupId().serialize(),
                                Optional.fromNullable(groupInfo.name),
                                new ArrayList<>(groupInfo.getMembers()),
                                createGroupAvatarAttachment(groupInfo.getGroupId()),
                                groupInfo.isMember(account.getSelfAddress()),
                                Optional.of(groupInfo.messageExpirationTime),
                                Optional.fromNullable(groupInfo.color),
                                groupInfo.blocked,
                                Optional.fromNullable(groupInfo.inboxPosition),
                                groupInfo.archived));
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
                logger.warn("Failed to delete groups temp file “{}”, ignoring: {}", groupsFile, e.getMessage());
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
                    IdentityInfo currentIdentity = account.getSignalProtocolStore().getIdentity(record.getAddress());
                    if (currentIdentity != null) {
                        verifiedMessage = new VerifiedMessage(record.getAddress(),
                                currentIdentity.getIdentityKey(),
                                currentIdentity.getTrustLevel().toVerifiedState(),
                                currentIdentity.getDateAdded().getTime());
                    }

                    ProfileKey profileKey = account.getProfileStore().getProfileKey(record.getAddress());
                    out.write(new DeviceContact(record.getAddress(),
                            Optional.fromNullable(record.name),
                            createContactAvatarAttachment(record.getAddress()),
                            Optional.fromNullable(record.color),
                            Optional.fromNullable(verifiedMessage),
                            Optional.fromNullable(profileKey),
                            record.blocked,
                            Optional.of(record.messageExpirationTime),
                            Optional.fromNullable(record.inboxPosition),
                            record.archived));
                }

                if (account.getProfileKey() != null) {
                    // Send our own profile key as well
                    out.write(new DeviceContact(account.getSelfAddress(),
                            Optional.absent(),
                            Optional.absent(),
                            Optional.absent(),
                            Optional.absent(),
                            Optional.of(account.getProfileKey()),
                            false,
                            Optional.absent(),
                            Optional.absent(),
                            false));
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
                logger.warn("Failed to delete contacts temp file “{}”, ignoring: {}", contactsFile, e.getMessage());
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
        for (GroupInfo record : getGroups()) {
            if (record.isBlocked()) {
                groupIds.add(record.getGroupId().serialize());
            }
        }
        sendSyncMessage(SignalServiceSyncMessage.forBlocked(new BlockedListMessage(addresses, groupIds)));
    }

    private void sendVerifiedMessage(
            SignalServiceAddress destination, IdentityKey identityKey, TrustLevel trustLevel
    ) throws IOException, UntrustedIdentityException {
        VerifiedMessage verifiedMessage = new VerifiedMessage(destination,
                identityKey,
                trustLevel.toVerifiedState(),
                System.currentTimeMillis());
        sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
    }

    public List<ContactInfo> getContacts() {
        return account.getContactStore().getContacts();
    }

    public String getContactOrProfileName(String number) {
        final SignalServiceAddress address = Utils.getSignalServiceAddressFromIdentifier(number);

        final ContactInfo contact = account.getContactStore().getContact(address);
        if (contact != null && !Util.isEmpty(contact.name)) {
            return contact.name;
        }

        final SignalProfileEntry profileEntry = account.getProfileStore().getProfileEntry(address);
        if (profileEntry != null && profileEntry.getProfile() != null) {
            return profileEntry.getProfile().getName();
        }

        return null;
    }

    public GroupInfo getGroup(GroupId groupId) {
        final GroupInfo group = account.getGroupStore().getGroup(groupId);
        if (group instanceof GroupInfoV2 && ((GroupInfoV2) group).getGroup() == null) {
            final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(((GroupInfoV2) group).getMasterKey());
            ((GroupInfoV2) group).setGroup(groupHelper.getDecryptedGroup(groupSecretParams));
            account.getGroupStore().updateGroup(group);
        }
        return group;
    }

    public List<IdentityInfo> getIdentities() {
        return account.getSignalProtocolStore().getIdentities();
    }

    public List<IdentityInfo> getIdentities(String number) throws InvalidNumberException {
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
        List<IdentityInfo> ids = account.getSignalProtocolStore().getIdentities(address);
        if (ids == null) {
            return false;
        }
        for (IdentityInfo id : ids) {
            if (!Arrays.equals(id.getIdentityKey().serialize(), fingerprint)) {
                continue;
            }

            account.getSignalProtocolStore()
                    .setIdentityTrustLevel(address, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            try {
                sendVerifiedMessage(address, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            } catch (IOException | UntrustedIdentityException e) {
                logger.warn("Failed to send verification sync message: {}", e.getMessage());
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
        List<IdentityInfo> ids = account.getSignalProtocolStore().getIdentities(address);
        if (ids == null) {
            return false;
        }
        for (IdentityInfo id : ids) {
            if (!safetyNumber.equals(computeSafetyNumber(address, id.getIdentityKey()))) {
                continue;
            }

            account.getSignalProtocolStore()
                    .setIdentityTrustLevel(address, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            try {
                sendVerifiedMessage(address, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            } catch (IOException | UntrustedIdentityException e) {
                logger.warn("Failed to send verification sync message: {}", e.getMessage());
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
        List<IdentityInfo> ids = account.getSignalProtocolStore().getIdentities(address);
        if (ids == null) {
            return false;
        }
        for (IdentityInfo id : ids) {
            if (id.getTrustLevel() == TrustLevel.UNTRUSTED) {
                account.getSignalProtocolStore()
                        .setIdentityTrustLevel(address, id.getIdentityKey(), TrustLevel.TRUSTED_UNVERIFIED);
                try {
                    sendVerifiedMessage(address, id.getIdentityKey(), TrustLevel.TRUSTED_UNVERIFIED);
                } catch (IOException | UntrustedIdentityException e) {
                    logger.warn("Failed to send verification sync message: {}", e.getMessage());
                }
            }
        }
        account.save();
        return true;
    }

    public String computeSafetyNumber(
            SignalServiceAddress theirAddress, IdentityKey theirIdentityKey
    ) {
        return Utils.computeSafetyNumber(ServiceConfig.capabilities.isUuid(),
                account.getSelfAddress(),
                getIdentityKeyPair().getPublicKey(),
                theirAddress,
                theirIdentityKey);
    }

    public SignalServiceAddress canonicalizeAndResolveSignalServiceAddress(String identifier) throws InvalidNumberException {
        String canonicalizedNumber = UuidUtil.isUuid(identifier)
                ? identifier
                : PhoneNumberFormatter.formatNumber(identifier, account.getUsername());
        return resolveSignalServiceAddress(canonicalizedNumber);
    }

    public SignalServiceAddress resolveSignalServiceAddress(String identifier) {
        SignalServiceAddress address = Utils.getSignalServiceAddressFromIdentifier(identifier);

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
        close(true);
    }

    void close(boolean closeAccount) throws IOException {
        executor.shutdown();

        if (messagePipe != null) {
            messagePipe.shutdown();
            messagePipe = null;
        }

        if (unidentifiedMessagePipe != null) {
            unidentifiedMessagePipe.shutdown();
            unidentifiedMessagePipe = null;
        }

        if (closeAccount && account != null) {
            account.close();
        }
        account = null;
    }

    public interface ReceiveMessageHandler {

        void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e);
    }
}
