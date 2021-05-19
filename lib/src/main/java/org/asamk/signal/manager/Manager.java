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

import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdV1;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.helper.GroupV2Helper;
import org.asamk.signal.manager.helper.PinHelper;
import org.asamk.signal.manager.helper.ProfileHelper;
import org.asamk.signal.manager.helper.UnidentifiedAccessHelper;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.manager.storage.messageCache.CachedMessage;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.stickers.Sticker;
import org.asamk.signal.manager.storage.stickers.StickerPackId;
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
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
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
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
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
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.api.util.SleepTimer;
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
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
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
    private final GroupV2Helper groupV2Helper;
    private final PinHelper pinHelper;
    private final AvatarStore avatarStore;
    private final AttachmentStore attachmentStore;
    private final SignalSessionLock sessionLock = new SignalSessionLock() {
        private final ReentrantLock LEGACY_LOCK = new ReentrantLock();

        @Override
        public Lock acquire() {
            LEGACY_LOCK.lock();
            return LEGACY_LOCK::unlock;
        }
    };

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
        final var keyBackupService = accountManager.getKeyBackupService(ServiceConfig.getIasKeyStore(),
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

        this.unidentifiedAccessHelper = new UnidentifiedAccessHelper(account::getProfileKey,
                account.getProfileStore()::getProfileKey,
                this::getRecipientProfile,
                this::getSenderCertificate);
        this.profileHelper = new ProfileHelper(account.getProfileStore()::getProfileKey,
                unidentifiedAccessHelper::getAccessFor,
                unidentified -> unidentified ? getOrCreateUnidentifiedMessagePipe() : getOrCreateMessagePipe(),
                () -> messageReceiver,
                this::resolveSignalServiceAddress);
        this.groupV2Helper = new GroupV2Helper(this::getRecipientProfileKeyCredential,
                this::getRecipientProfile,
                account::getSelfRecipientId,
                groupsV2Operations,
                groupsV2Api,
                this::getGroupAuthForToday,
                this::resolveSignalServiceAddress);
        this.avatarStore = new AvatarStore(pathConfig.getAvatarsPath());
        this.attachmentStore = new AttachmentStore(pathConfig.getAttachmentsPath());
    }

    public String getUsername() {
        return account.getUsername();
    }

    public SignalServiceAddress getSelfAddress() {
        return account.getSelfAddress();
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
            String username, File settingsPath, ServiceEnvironment serviceEnvironment, String userAgent
    ) throws IOException, NotRegisteredException {
        var pathConfig = PathConfig.createDefault(settingsPath);

        if (!SignalAccount.userExists(pathConfig.getDataPath(), username)) {
            throw new NotRegisteredException();
        }

        var account = SignalAccount.load(pathConfig.getDataPath(), username, true);

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
        if (accountManager.getPreKeysCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
            refreshPreKeys();
        }
        if (account.getUuid() == null) {
            account.setUuid(accountManager.getOwnUuid());
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
        var contactDetails = getRegisteredUsers(numbers);

        var registeredUsers = contactDetails.keySet();

        return numbers.stream().collect(Collectors.toMap(x -> x, registeredUsers::contains));
    }

    public void updateAccountAttributes() throws IOException {
        accountManager.setAccountAttributes(account.getEncryptedDeviceName(),
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
        var profile = getRecipientProfile(account.getSelfRecipientId());
        var builder = profile == null ? Profile.newBuilder() : Profile.newBuilder(profile);
        if (givenName != null) {
            builder.withGivenName(givenName);
        }
        if (familyName != null) {
            builder.withFamilyName(familyName);
        }
        if (about != null) {
            builder.withAbout(about);
        }
        if (aboutEmoji != null) {
            builder.withAboutEmoji(aboutEmoji);
        }
        var newProfile = builder.build();

        try (final var streamDetails = avatar == null
                ? avatarStore.retrieveProfileAvatar(getSelfAddress())
                : avatar.isPresent() ? Utils.createStreamDetailsFromFile(avatar.get()) : null) {
            accountManager.setVersionedProfile(account.getUuid(),
                    account.getProfileKey(),
                    newProfile.getInternalServiceName(),
                    newProfile.getAbout() == null ? "" : newProfile.getAbout(),
                    newProfile.getAboutEmoji() == null ? "" : newProfile.getAboutEmoji(),
                    Optional.absent(),
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
        account.getProfileStore().storeProfile(account.getSelfRecipientId(), newProfile);

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

        account.setRegistered(false);
    }

    public void deleteAccount() throws IOException {
        accountManager.deleteAccount();

        account.setRegistered(false);
    }

    public List<Device> getLinkedDevices() throws IOException {
        var devices = accountManager.getDevices();
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
        accountManager.removeDevice(deviceId);
        var devices = accountManager.getDevices();
        account.setMultiDevice(devices.size() > 1);
    }

    public void addDeviceLink(URI linkUri) throws IOException, InvalidKeyException {
        var info = DeviceLinkInfo.parseDeviceLinkUri(linkUri);

        addDevice(info.deviceIdentifier, info.deviceKey);
    }

    private void addDevice(String deviceIdentifier, ECPublicKey deviceKey) throws IOException, InvalidKeyException {
        var identityKeyPair = getIdentityKeyPair();
        var verificationCode = accountManager.getNewDeviceVerificationCode();

        accountManager.addDevice(deviceIdentifier,
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
            // Remove legacy registration lock
            accountManager.removeRegistrationLockV1();

            // Remove KBS Pin
            pinHelper.removeRegistrationLockPin();

            account.setRegistrationLockPin(null, null);
        }
    }

    void refreshPreKeys() throws IOException {
        var oneTimePreKeys = generatePreKeys();
        final var identityKeyPair = getIdentityKeyPair();
        var signedPreKeyRecord = generateSignedPreKey(identityKeyPair);

        accountManager.setPreKeys(identityKeyPair.getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
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
                sessionLock,
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

    public Profile getRecipientProfile(
            RecipientId recipientId
    ) {
        return getRecipientProfile(recipientId, false);
    }

    private final Set<RecipientId> pendingProfileRequest = new HashSet<>();

    Profile getRecipientProfile(
            RecipientId recipientId, boolean force
    ) {
        var profile = account.getProfileStore().getProfile(recipientId);

        var now = new Date().getTime();
        // Profiles are cached for 24h before retrieving them again, unless forced
        if (!force && profile != null && now - profile.getLastUpdateTimestamp() < 24 * 60 * 60 * 1000) {
            return profile;
        }

        synchronized (pendingProfileRequest) {
            if (pendingProfileRequest.contains(recipientId)) {
                return profile;
            }
            pendingProfileRequest.add(recipientId);
        }
        final SignalServiceProfile encryptedProfile;
        try {
            encryptedProfile = retrieveEncryptedProfile(recipientId);
        } finally {
            synchronized (pendingProfileRequest) {
                pendingProfileRequest.remove(recipientId);
            }
        }
        if (encryptedProfile == null) {
            return null;
        }

        var profileKey = account.getProfileStore().getProfileKey(recipientId);
        if (profileKey == null) {
            profile = new Profile(new Date().getTime(),
                    null,
                    null,
                    null,
                    null,
                    ProfileUtils.getUnidentifiedAccessMode(encryptedProfile, null),
                    ProfileUtils.getCapabilities(encryptedProfile));
        } else {
            profile = decryptProfileAndDownloadAvatar(recipientId, profileKey, encryptedProfile);
        }
        account.getProfileStore().storeProfile(recipientId, profile);

        return profile;
    }

    private SignalServiceProfile retrieveEncryptedProfile(RecipientId recipientId) {
        try {
            return retrieveProfileAndCredential(recipientId, SignalServiceProfile.RequestType.PROFILE).getProfile();
        } catch (IOException e) {
            logger.warn("Failed to retrieve profile, ignoring: {}", e.getMessage());
            return null;
        }
    }

    private ProfileAndCredential retrieveProfileAndCredential(
            final RecipientId recipientId, final SignalServiceProfile.RequestType requestType
    ) throws IOException {
        final var profileAndCredential = profileHelper.retrieveProfileSync(recipientId, requestType);
        final var profile = profileAndCredential.getProfile();

        try {
            var newIdentity = account.getIdentityKeyStore()
                    .saveIdentity(recipientId,
                            new IdentityKey(Base64.getDecoder().decode(profile.getIdentityKey())),
                            new Date());

            if (newIdentity) {
                account.getSessionStore().archiveSessions(recipientId);
            }
        } catch (InvalidKeyException ignored) {
            logger.warn("Got invalid identity key in profile for {}",
                    resolveSignalServiceAddress(recipientId).getIdentifier());
        }
        return profileAndCredential;
    }

    private ProfileKeyCredential getRecipientProfileKeyCredential(RecipientId recipientId) {
        var profileKeyCredential = account.getProfileStore().getProfileKeyCredential(recipientId);
        if (profileKeyCredential != null) {
            return profileKeyCredential;
        }

        ProfileAndCredential profileAndCredential;
        try {
            profileAndCredential = retrieveProfileAndCredential(recipientId,
                    SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL);
        } catch (IOException e) {
            logger.warn("Failed to retrieve profile key credential, ignoring: {}", e.getMessage());
            return null;
        }

        profileKeyCredential = profileAndCredential.getProfileKeyCredential().orNull();
        account.getProfileStore().storeProfileKeyCredential(recipientId, profileKeyCredential);

        var profileKey = account.getProfileStore().getProfileKey(recipientId);
        if (profileKey != null) {
            final var profile = decryptProfileAndDownloadAvatar(recipientId,
                    profileKey,
                    profileAndCredential.getProfile());
            account.getProfileStore().storeProfile(recipientId, profile);
        }

        return profileKeyCredential;
    }

    private Profile decryptProfileAndDownloadAvatar(
            final RecipientId recipientId, final ProfileKey profileKey, final SignalServiceProfile encryptedProfile
    ) {
        if (encryptedProfile.getAvatar() != null) {
            downloadProfileAvatar(resolveSignalServiceAddress(recipientId), encryptedProfile.getAvatar(), profileKey);
        }

        return ProfileUtils.decryptProfile(profileKey, encryptedProfile);
    }

    private Optional<SignalServiceAttachmentStream> createGroupAvatarAttachment(GroupId groupId) throws IOException {
        final var streamDetails = avatarStore.retrieveGroupAvatar(groupId);
        if (streamDetails == null) {
            return Optional.absent();
        }

        return Optional.of(AttachmentUtils.createAttachment(streamDetails, Optional.absent()));
    }

    private Optional<SignalServiceAttachmentStream> createContactAvatarAttachment(SignalServiceAddress address) throws IOException {
        final var streamDetails = avatarStore.retrieveContactAvatar(address);
        if (streamDetails == null) {
            return Optional.absent();
        }

        return Optional.of(AttachmentUtils.createAttachment(streamDetails, Optional.absent()));
    }

    private GroupInfo getGroupForSending(GroupId groupId) throws GroupNotFoundException, NotAGroupMemberException {
        var g = getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        if (!g.isMember(account.getSelfRecipientId())) {
            throw new NotAGroupMemberException(groupId, g.getTitle());
        }
        return g;
    }

    private GroupInfo getGroupForUpdating(GroupId groupId) throws GroupNotFoundException, NotAGroupMemberException {
        var g = getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        if (!g.isMember(account.getSelfRecipientId()) && !g.isPendingMember(account.getSelfRecipientId())) {
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
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(AttachmentUtils.getSignalServiceAttachments(attachments));
        }

        return sendGroupMessage(messageBuilder, groupId);
    }

    public Pair<Long, List<SendMessageResult>> sendGroupMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, GroupId groupId
    ) throws IOException, InvalidNumberException, NotAGroupMemberException, GroupNotFoundException {
        var targetAuthorRecipientId = canonicalizeAndResolveRecipient(targetAuthor);
        var reaction = new SignalServiceDataMessage.Reaction(emoji,
                remove,
                resolveSignalServiceAddress(targetAuthorRecipientId),
                targetSentTimestamp);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withReaction(reaction);

        return sendGroupMessage(messageBuilder, groupId);
    }

    public Pair<Long, List<SendMessageResult>> sendGroupMessage(
            SignalServiceDataMessage.Builder messageBuilder, GroupId groupId
    ) throws IOException, GroupNotFoundException, NotAGroupMemberException {
        final var g = getGroupForSending(groupId);

        GroupUtils.setGroupContext(messageBuilder, g);
        messageBuilder.withExpiration(g.getMessageExpirationTime());

        return sendMessage(messageBuilder, g.getMembersWithout(account.getSelfRecipientId()));
    }

    public Pair<Long, List<SendMessageResult>> sendQuitGroupMessage(
            GroupId groupId, Set<String> groupAdmins
    ) throws GroupNotFoundException, IOException, NotAGroupMemberException, InvalidNumberException, LastGroupAdminException {
        SignalServiceDataMessage.Builder messageBuilder;

        final var g = getGroupForUpdating(groupId);
        if (g instanceof GroupInfoV1) {
            var groupInfoV1 = (GroupInfoV1) g;
            var group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.QUIT).withId(groupId.serialize()).build();
            messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group);
            groupInfoV1.removeMember(account.getSelfRecipientId());
            account.getGroupStore().updateGroup(groupInfoV1);
        } else {
            final var groupInfoV2 = (GroupInfoV2) g;
            final var currentAdmins = g.getAdminMembers();
            final var newAdmins = getSignalServiceAddresses(groupAdmins);
            newAdmins.removeAll(currentAdmins);
            newAdmins.retainAll(g.getMembers());
            if (currentAdmins.contains(getSelfRecipientId())
                    && currentAdmins.size() == 1
                    && g.getMembers().size() > 1
                    && newAdmins.size() == 0) {
                // Last admin can't leave the group, unless she's also the last member
                throw new LastGroupAdminException(g.getGroupId(), g.getTitle());
            }
            final var groupGroupChangePair = groupV2Helper.leaveGroup(groupInfoV2, newAdmins);
            groupInfoV2.setGroup(groupGroupChangePair.first(), this::resolveRecipient);
            messageBuilder = getGroupUpdateMessageBuilder(groupInfoV2, groupGroupChangePair.second().toByteArray());
            account.getGroupStore().updateGroup(groupInfoV2);
        }

        return sendMessage(messageBuilder, g.getMembersWithout(account.getSelfRecipientId()));
    }

    public Pair<GroupId, List<SendMessageResult>> createGroup(
            String name, List<String> members, File avatarFile
    ) throws IOException, AttachmentInvalidException, InvalidNumberException {
        return createGroup(name, members == null ? null : getSignalServiceAddresses(members), avatarFile);
    }

    private Pair<GroupId, List<SendMessageResult>> createGroup(
            String name, Set<RecipientId> members, File avatarFile
    ) throws IOException, AttachmentInvalidException {
        final var selfRecipientId = account.getSelfRecipientId();
        if (members != null && members.contains(selfRecipientId)) {
            members = new HashSet<>(members);
            members.remove(selfRecipientId);
        }

        var gv2Pair = groupV2Helper.createGroup(name == null ? "" : name,
                members == null ? Set.of() : members,
                avatarFile);

        SignalServiceDataMessage.Builder messageBuilder;
        if (gv2Pair == null) {
            // Failed to create v2 group, creating v1 group instead
            var gv1 = new GroupInfoV1(GroupIdV1.createRandom());
            gv1.addMembers(List.of(selfRecipientId));
            final var result = updateGroupV1(gv1, name, members, avatarFile);
            return new Pair<>(gv1.getGroupId(), result.second());
        }

        final var gv2 = gv2Pair.first();
        final var decryptedGroup = gv2Pair.second();

        gv2.setGroup(decryptedGroup, this::resolveRecipient);
        if (avatarFile != null) {
            avatarStore.storeGroupAvatar(gv2.getGroupId(),
                    outputStream -> IOUtils.copyFileToStream(avatarFile, outputStream));
        }
        messageBuilder = getGroupUpdateMessageBuilder(gv2, null);
        account.getGroupStore().updateGroup(gv2);

        final var result = sendMessage(messageBuilder, gv2.getMembersIncludingPendingWithout(selfRecipientId));
        return new Pair<>(gv2.getGroupId(), result.second());
    }

    public Pair<Long, List<SendMessageResult>> updateGroup(
            GroupId groupId,
            String name,
            String description,
            List<String> members,
            List<String> removeMembers,
            List<String> admins,
            List<String> removeAdmins,
            boolean resetGroupLink,
            GroupLinkState groupLinkState,
            GroupPermission addMemberPermission,
            GroupPermission editDetailsPermission,
            File avatarFile,
            Integer expirationTimer
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, InvalidNumberException, NotAGroupMemberException {
        return updateGroup(groupId,
                name,
                description,
                members == null ? null : getSignalServiceAddresses(members),
                removeMembers == null ? null : getSignalServiceAddresses(removeMembers),
                admins == null ? null : getSignalServiceAddresses(admins),
                removeAdmins == null ? null : getSignalServiceAddresses(removeAdmins),
                resetGroupLink,
                groupLinkState,
                addMemberPermission,
                editDetailsPermission,
                avatarFile,
                expirationTimer);
    }

    private Pair<Long, List<SendMessageResult>> updateGroup(
            final GroupId groupId,
            final String name,
            final String description,
            final Set<RecipientId> members,
            final Set<RecipientId> removeMembers,
            final Set<RecipientId> admins,
            final Set<RecipientId> removeAdmins,
            final boolean resetGroupLink,
            final GroupLinkState groupLinkState,
            final GroupPermission addMemberPermission,
            final GroupPermission editDetailsPermission,
            final File avatarFile,
            final Integer expirationTimer
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException {
        var group = getGroupForUpdating(groupId);

        if (group instanceof GroupInfoV2) {
            return updateGroupV2((GroupInfoV2) group,
                    name,
                    description,
                    members,
                    removeMembers,
                    admins,
                    removeAdmins,
                    resetGroupLink,
                    groupLinkState,
                    addMemberPermission,
                    editDetailsPermission,
                    avatarFile,
                    expirationTimer);
        }

        final var gv1 = (GroupInfoV1) group;
        final var result = updateGroupV1(gv1, name, members, avatarFile);
        if (expirationTimer != null) {
            setExpirationTimer(gv1, expirationTimer);
        }
        return result;
    }

    private Pair<Long, List<SendMessageResult>> updateGroupV1(
            final GroupInfoV1 gv1, final String name, final Set<RecipientId> members, final File avatarFile
    ) throws IOException, AttachmentInvalidException {
        updateGroupV1Details(gv1, name, members, avatarFile);
        var messageBuilder = getGroupUpdateMessageBuilder(gv1);

        account.getGroupStore().updateGroup(gv1);

        return sendMessage(messageBuilder, gv1.getMembersIncludingPendingWithout(account.getSelfRecipientId()));
    }

    private void updateGroupV1Details(
            final GroupInfoV1 g, final String name, final Collection<RecipientId> members, final File avatarFile
    ) throws IOException {
        if (name != null) {
            g.name = name;
        }

        if (members != null) {
            final var newMemberAddresses = members.stream()
                    .filter(member -> !g.isMember(member))
                    .map(this::resolveSignalServiceAddress)
                    .collect(Collectors.toList());
            final var newE164Members = new HashSet<String>();
            for (var member : newMemberAddresses) {
                if (!member.getNumber().isPresent()) {
                    continue;
                }
                newE164Members.add(member.getNumber().get());
            }

            final var registeredUsers = getRegisteredUsers(newE164Members);
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

    private Pair<Long, List<SendMessageResult>> updateGroupV2(
            final GroupInfoV2 group,
            final String name,
            final String description,
            final Set<RecipientId> members,
            final Set<RecipientId> removeMembers,
            final Set<RecipientId> admins,
            final Set<RecipientId> removeAdmins,
            final boolean resetGroupLink,
            final GroupLinkState groupLinkState,
            final GroupPermission addMemberPermission,
            final GroupPermission editDetailsPermission,
            final File avatarFile,
            Integer expirationTimer
    ) throws IOException {
        Pair<Long, List<SendMessageResult>> result = null;
        if (group.isPendingMember(account.getSelfRecipientId())) {
            var groupGroupChangePair = groupV2Helper.acceptInvite(group);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (members != null) {
            final var newMembers = new HashSet<>(members);
            newMembers.removeAll(group.getMembers());
            if (newMembers.size() > 0) {
                var groupGroupChangePair = groupV2Helper.addMembers(group, newMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }
        }

        if (removeMembers != null) {
            var existingRemoveMembers = new HashSet<>(removeMembers);
            existingRemoveMembers.retainAll(group.getMembers());
            existingRemoveMembers.remove(getSelfRecipientId());// self can be removed with sendQuitGroupMessage
            if (existingRemoveMembers.size() > 0) {
                var groupGroupChangePair = groupV2Helper.removeMembers(group, existingRemoveMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }

            var pendingRemoveMembers = new HashSet<>(removeMembers);
            pendingRemoveMembers.retainAll(group.getPendingMembers());
            if (pendingRemoveMembers.size() > 0) {
                var groupGroupChangePair = groupV2Helper.revokeInvitedMembers(group, pendingRemoveMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }
        }

        if (admins != null) {
            final var newAdmins = new HashSet<>(admins);
            newAdmins.retainAll(group.getMembers());
            newAdmins.removeAll(group.getAdminMembers());
            if (newAdmins.size() > 0) {
                for (var admin : newAdmins) {
                    var groupGroupChangePair = groupV2Helper.setMemberAdmin(group, admin, true);
                    result = sendUpdateGroupV2Message(group,
                            groupGroupChangePair.first(),
                            groupGroupChangePair.second());
                }
            }
        }

        if (removeAdmins != null) {
            final var existingRemoveAdmins = new HashSet<>(removeAdmins);
            existingRemoveAdmins.retainAll(group.getAdminMembers());
            if (existingRemoveAdmins.size() > 0) {
                for (var admin : existingRemoveAdmins) {
                    var groupGroupChangePair = groupV2Helper.setMemberAdmin(group, admin, false);
                    result = sendUpdateGroupV2Message(group,
                            groupGroupChangePair.first(),
                            groupGroupChangePair.second());
                }
            }
        }

        if (resetGroupLink) {
            var groupGroupChangePair = groupV2Helper.resetGroupLinkPassword(group);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (groupLinkState != null) {
            var groupGroupChangePair = groupV2Helper.setGroupLinkState(group, groupLinkState);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (addMemberPermission != null) {
            var groupGroupChangePair = groupV2Helper.setAddMemberPermission(group, addMemberPermission);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (editDetailsPermission != null) {
            var groupGroupChangePair = groupV2Helper.setEditDetailsPermission(group, editDetailsPermission);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (expirationTimer != null) {
            var groupGroupChangePair = groupV2Helper.setMessageExpirationTimer(group, expirationTimer);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (name != null || description != null || avatarFile != null) {
            var groupGroupChangePair = groupV2Helper.updateGroup(group, name, description, avatarFile);
            if (avatarFile != null) {
                avatarStore.storeGroupAvatar(group.getGroupId(),
                        outputStream -> IOUtils.copyFileToStream(avatarFile, outputStream));
            }
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        return result;
    }

    public Pair<GroupId, List<SendMessageResult>> joinGroup(
            GroupInviteLinkUrl inviteLinkUrl
    ) throws IOException, GroupLinkNotActiveException {
        final var groupJoinInfo = groupV2Helper.getDecryptedGroupJoinInfo(inviteLinkUrl.getGroupMasterKey(),
                inviteLinkUrl.getPassword());
        final var groupChange = groupV2Helper.joinGroup(inviteLinkUrl.getGroupMasterKey(),
                inviteLinkUrl.getPassword(),
                groupJoinInfo);
        final var group = getOrMigrateGroup(inviteLinkUrl.getGroupMasterKey(),
                groupJoinInfo.getRevision() + 1,
                groupChange.toByteArray());

        if (group.getGroup() == null) {
            // Only requested member, can't send update to group members
            return new Pair<>(group.getGroupId(), List.of());
        }

        final var result = sendUpdateGroupV2Message(group, group.getGroup(), groupChange);

        return new Pair<>(group.getGroupId(), result.second());
    }

    private Pair<Long, List<SendMessageResult>> sendUpdateGroupV2Message(
            GroupInfoV2 group, DecryptedGroup newDecryptedGroup, GroupChange groupChange
    ) throws IOException {
        final var selfRecipientId = account.getSelfRecipientId();
        final var members = group.getMembersIncludingPendingWithout(selfRecipientId);
        group.setGroup(newDecryptedGroup, this::resolveRecipient);
        members.addAll(group.getMembersIncludingPendingWithout(selfRecipientId));

        final var messageBuilder = getGroupUpdateMessageBuilder(group, groupChange.toByteArray());
        account.getGroupStore().updateGroup(group);
        return sendMessage(messageBuilder, members);
    }

    private static int currentTimeDays() {
        return (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    }

    private GroupsV2AuthorizationString getGroupAuthForToday(
            final GroupSecretParams groupSecretParams
    ) throws IOException {
        final var today = currentTimeDays();
        // Returns credentials for the next 7 days
        final var credentials = groupsV2Api.getCredentials(today);
        // TODO cache credentials until they expire
        var authCredentialResponse = credentials.get(today);
        try {
            return groupsV2Api.getGroupsV2AuthorizationString(account.getUuid(),
                    today,
                    groupSecretParams,
                    authCredentialResponse);
        } catch (VerificationFailedException e) {
            throw new IOException(e);
        }
    }

    Pair<Long, List<SendMessageResult>> sendGroupInfoMessage(
            GroupIdV1 groupId, SignalServiceAddress recipient
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, AttachmentInvalidException {
        GroupInfoV1 g;
        var group = getGroupForSending(groupId);
        if (!(group instanceof GroupInfoV1)) {
            throw new RuntimeException("Received an invalid group request for a v2 group!");
        }
        g = (GroupInfoV1) group;

        final var recipientId = resolveRecipient(recipient);
        if (!g.isMember(recipientId)) {
            throw new NotAGroupMemberException(groupId, g.name);
        }

        var messageBuilder = getGroupUpdateMessageBuilder(g);

        // Send group message only to the recipient who requested it
        return sendMessage(messageBuilder, Set.of(recipientId));
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfoV1 g) throws AttachmentInvalidException {
        var group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                .withId(g.getGroupId().serialize())
                .withName(g.name)
                .withMembers(g.getMembers()
                        .stream()
                        .map(this::resolveSignalServiceAddress)
                        .collect(Collectors.toList()));

        try {
            final var attachment = createGroupAvatarAttachment(g.getGroupId());
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
        var group = SignalServiceGroupV2.newBuilder(g.getMasterKey())
                .withRevision(g.getGroup().getRevision())
                .withSignedGroupChange(signedGroupChange);
        return SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build())
                .withExpiration(g.getMessageExpirationTime());
    }

    Pair<Long, List<SendMessageResult>> sendGroupInfoRequest(
            GroupIdV1 groupId, SignalServiceAddress recipient
    ) throws IOException {
        var group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.REQUEST_INFO).withId(groupId.serialize());

        var messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group.build());

        // Send group info request message to the recipient who sent us a message with this groupId
        return sendMessage(messageBuilder, Set.of(resolveRecipient(recipient)));
    }

    void sendReceipt(
            SignalServiceAddress remoteAddress, long messageId
    ) throws IOException, UntrustedIdentityException {
        var receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.DELIVERY,
                List.of(messageId),
                System.currentTimeMillis());

        createMessageSender().sendReceipt(remoteAddress,
                unidentifiedAccessHelper.getAccessFor(resolveRecipient(remoteAddress)),
                receiptMessage);
    }

    public Pair<Long, List<SendMessageResult>> sendMessage(
            String messageText, List<String> attachments, List<String> recipients
    ) throws IOException, AttachmentInvalidException, InvalidNumberException {
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            var attachmentStreams = AttachmentUtils.getSignalServiceAttachments(attachments);

            // Upload attachments here, so we only upload once even for multiple recipients
            var messageSender = createMessageSender();
            var attachmentPointers = new ArrayList<SignalServiceAttachment>(attachmentStreams.size());
            for (var attachment : attachmentStreams) {
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
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(AttachmentUtils.getSignalServiceAttachments(attachments));
        }
        return sendSelfMessage(messageBuilder);
    }

    public Pair<Long, List<SendMessageResult>> sendRemoteDeleteMessage(
            long targetSentTimestamp, List<String> recipients
    ) throws IOException, InvalidNumberException {
        var delete = new SignalServiceDataMessage.RemoteDelete(targetSentTimestamp);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withRemoteDelete(delete);
        return sendMessage(messageBuilder, getSignalServiceAddresses(recipients));
    }

    public Pair<Long, List<SendMessageResult>> sendGroupRemoteDeleteMessage(
            long targetSentTimestamp, GroupId groupId
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException {
        var delete = new SignalServiceDataMessage.RemoteDelete(targetSentTimestamp);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withRemoteDelete(delete);
        return sendGroupMessage(messageBuilder, groupId);
    }

    public Pair<Long, List<SendMessageResult>> sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients
    ) throws IOException, InvalidNumberException {
        var targetAuthorRecipientId = canonicalizeAndResolveRecipient(targetAuthor);
        var reaction = new SignalServiceDataMessage.Reaction(emoji,
                remove,
                resolveSignalServiceAddress(targetAuthorRecipientId),
                targetSentTimestamp);
        final var messageBuilder = SignalServiceDataMessage.newBuilder().withReaction(reaction);
        return sendMessage(messageBuilder, getSignalServiceAddresses(recipients));
    }

    public Pair<Long, List<SendMessageResult>> sendEndSessionMessage(List<String> recipients) throws IOException, InvalidNumberException {
        var messageBuilder = SignalServiceDataMessage.newBuilder().asEndSessionMessage();

        final var signalServiceAddresses = getSignalServiceAddresses(recipients);
        try {
            return sendMessage(messageBuilder, signalServiceAddresses);
        } catch (Exception e) {
            for (var address : signalServiceAddresses) {
                handleEndSession(address);
            }
            throw e;
        }
    }

    void renewSession(RecipientId recipientId) throws IOException {
        account.getSessionStore().archiveSessions(recipientId);
        if (!recipientId.equals(getSelfRecipientId())) {
            sendNullMessage(recipientId);
        }
    }

    public String getContactName(String number) throws InvalidNumberException {
        var contact = account.getContactStore().getContact(canonicalizeAndResolveRecipient(number));
        return contact == null || contact.getName() == null ? "" : contact.getName();
    }

    public void setContactName(String number, String name) throws InvalidNumberException, NotMasterDeviceException {
        if (!account.isMasterDevice()) {
            throw new NotMasterDeviceException();
        }
        final var recipientId = canonicalizeAndResolveRecipient(number);
        var contact = account.getContactStore().getContact(recipientId);
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        account.getContactStore().storeContact(recipientId, builder.withName(name).build());
    }

    public void setContactBlocked(
            String number, boolean blocked
    ) throws InvalidNumberException, NotMasterDeviceException {
        if (!account.isMasterDevice()) {
            throw new NotMasterDeviceException();
        }
        setContactBlocked(canonicalizeAndResolveRecipient(number), blocked);
    }

    private void setContactBlocked(RecipientId recipientId, boolean blocked) {
        var contact = account.getContactStore().getContact(recipientId);
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        account.getContactStore().storeContact(recipientId, builder.withBlocked(blocked).build());
    }

    public void setGroupBlocked(final GroupId groupId, final boolean blocked) throws GroupNotFoundException {
        var group = getGroup(groupId);
        if (group == null) {
            throw new GroupNotFoundException(groupId);
        }

        group.setBlocked(blocked);
        account.getGroupStore().updateGroup(group);
    }

    private void setExpirationTimer(RecipientId recipientId, int messageExpirationTimer) {
        var contact = account.getContactStore().getContact(recipientId);
        if (contact != null && contact.getMessageExpirationTime() == messageExpirationTimer) {
            return;
        }
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        account.getContactStore()
                .storeContact(recipientId, builder.withMessageExpirationTime(messageExpirationTimer).build());
    }

    private void sendExpirationTimerUpdate(RecipientId recipientId) throws IOException {
        final var messageBuilder = SignalServiceDataMessage.newBuilder().asExpirationUpdate();
        sendMessage(messageBuilder, Set.of(recipientId));
    }

    /**
     * Change the expiration timer for a contact
     */
    public void setExpirationTimer(
            String number, int messageExpirationTimer
    ) throws IOException, InvalidNumberException {
        var recipientId = canonicalizeAndResolveRecipient(number);
        setExpirationTimer(recipientId, messageExpirationTimer);
        sendExpirationTimerUpdate(recipientId);
    }

    /**
     * Change the expiration timer for a group
     */
    private void setExpirationTimer(
            GroupInfoV1 groupInfoV1, int messageExpirationTimer
    ) throws NotAGroupMemberException, GroupNotFoundException, IOException {
        groupInfoV1.messageExpirationTime = messageExpirationTimer;
        account.getGroupStore().updateGroup(groupInfoV1);
        sendExpirationTimerUpdate(groupInfoV1.getGroupId());
    }

    private void sendExpirationTimerUpdate(GroupIdV1 groupId) throws IOException, NotAGroupMemberException, GroupNotFoundException {
        final var messageBuilder = SignalServiceDataMessage.newBuilder().asExpirationUpdate();
        sendGroupMessage(messageBuilder, groupId);
    }

    /**
     * Upload the sticker pack from path.
     *
     * @param path Path can be a path to a manifest.json file or to a zip file that contains a manifest.json file
     * @return if successful, returns the URL to install the sticker pack in the signal app
     */
    public String uploadStickerPack(File path) throws IOException, StickerPackInvalidException {
        var manifest = StickerUtils.getSignalServiceStickerManifestUpload(path);

        var messageSender = createMessageSender();

        var packKey = KeyUtils.createStickerUploadKey();
        var packId = messageSender.uploadStickerManifest(manifest, packKey);

        var sticker = new Sticker(StickerPackId.deserialize(Hex.fromStringCondensed(packId)), packKey);
        account.getStickerStore().updateSticker(sticker);

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

    public void requestAllSyncData() throws IOException {
        requestSyncGroups();
        requestSyncContacts();
        requestSyncBlocked();
        requestSyncConfiguration();
        requestSyncKeys();
    }

    private void requestSyncGroups() throws IOException {
        var r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS)
                .build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            throw new AssertionError(e);
        }
    }

    private void requestSyncContacts() throws IOException {
        var r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS)
                .build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            throw new AssertionError(e);
        }
    }

    private void requestSyncBlocked() throws IOException {
        var r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.BLOCKED)
                .build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            throw new AssertionError(e);
        }
    }

    private void requestSyncConfiguration() throws IOException {
        var r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.CONFIGURATION)
                .build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            throw new AssertionError(e);
        }
    }

    private void requestSyncKeys() throws IOException {
        var r = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.KEYS)
                .build();
        var message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            throw new AssertionError(e);
        }
    }

    private byte[] getSenderCertificate() {
        byte[] certificate;
        try {
            if (account.isPhoneNumberShared()) {
                certificate = accountManager.getSenderCertificate();
            } else {
                certificate = accountManager.getSenderCertificateForPhoneNumberPrivacy();
            }
        } catch (IOException e) {
            logger.warn("Failed to get sender certificate, ignoring: {}", e.getMessage());
            return null;
        }
        // TODO cache for a day
        return certificate;
    }

    private void sendSyncMessage(SignalServiceSyncMessage message) throws IOException, UntrustedIdentityException {
        var messageSender = createMessageSender();
        messageSender.sendMessage(message, unidentifiedAccessHelper.getAccessForSync());
    }

    private Set<RecipientId> getSignalServiceAddresses(Collection<String> numbers) throws InvalidNumberException {
        final var signalServiceAddresses = new HashSet<SignalServiceAddress>(numbers.size());
        final var addressesMissingUuid = new HashSet<SignalServiceAddress>();

        for (var number : numbers) {
            final var resolvedAddress = resolveSignalServiceAddress(canonicalizeAndResolveRecipient(number));
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
        try {
            return accountManager.getRegisteredUsers(ServiceConfig.getIasKeyStore(),
                    numbers,
                    serviceEnvironmentConfig.getCdsMrenclave());
        } catch (Quote.InvalidQuoteFormatException | UnauthenticatedQuoteException | SignatureException | UnauthenticatedResponseException | InvalidKeyException e) {
            throw new IOException(e);
        }
    }

    private Pair<Long, List<SendMessageResult>> sendMessage(
            SignalServiceDataMessage.Builder messageBuilder, Set<RecipientId> recipientIds
    ) throws IOException {
        final var timestamp = System.currentTimeMillis();
        messageBuilder.withTimestamp(timestamp);
        getOrCreateMessagePipe();
        getOrCreateUnidentifiedMessagePipe();
        SignalServiceDataMessage message = null;
        try {
            message = messageBuilder.build();
            if (message.getGroupContext().isPresent()) {
                try {
                    var messageSender = createMessageSender();
                    final var isRecipientUpdate = false;
                    final var recipientIdList = new ArrayList<>(recipientIds);
                    final var addresses = recipientIdList.stream()
                            .map(this::resolveSignalServiceAddress)
                            .collect(Collectors.toList());
                    var result = messageSender.sendMessage(addresses,
                            unidentifiedAccessHelper.getAccessFor(recipientIdList),
                            isRecipientUpdate,
                            message);

                    for (var r : result) {
                        if (r.getIdentityFailure() != null) {
                            final var recipientId = resolveRecipient(r.getAddress());
                            final var newIdentity = account.getIdentityKeyStore()
                                    .saveIdentity(recipientId, r.getIdentityFailure().getIdentityKey(), new Date());
                            if (newIdentity) {
                                account.getSessionStore().archiveSessions(recipientId);
                            }
                        }
                    }

                    return new Pair<>(timestamp, result);
                } catch (UntrustedIdentityException e) {
                    return new Pair<>(timestamp, List.of());
                }
            } else {
                // Send to all individually, so sync messages are sent correctly
                messageBuilder.withProfileKey(account.getProfileKey().serialize());
                var results = new ArrayList<SendMessageResult>(recipientIds.size());
                for (var recipientId : recipientIds) {
                    final var contact = account.getContactStore().getContact(recipientId);
                    final var expirationTime = contact != null ? contact.getMessageExpirationTime() : 0;
                    messageBuilder.withExpiration(expirationTime);
                    message = messageBuilder.build();
                    results.add(sendMessage(recipientId, message));
                }
                return new Pair<>(timestamp, results);
            }
        } finally {
            if (message != null && message.isEndSession()) {
                for (var recipient : recipientIds) {
                    handleEndSession(recipient);
                }
            }
        }
    }

    private Pair<Long, SendMessageResult> sendSelfMessage(
            SignalServiceDataMessage.Builder messageBuilder
    ) throws IOException {
        final var timestamp = System.currentTimeMillis();
        messageBuilder.withTimestamp(timestamp);
        getOrCreateMessagePipe();
        getOrCreateUnidentifiedMessagePipe();
        final var recipientId = account.getSelfRecipientId();

        final var contact = account.getContactStore().getContact(recipientId);
        final var expirationTime = contact != null ? contact.getMessageExpirationTime() : 0;
        messageBuilder.withExpiration(expirationTime);

        var message = messageBuilder.build();
        final var result = sendSelfMessage(message);
        return new Pair<>(timestamp, result);
    }

    private SendMessageResult sendSelfMessage(SignalServiceDataMessage message) throws IOException {
        var messageSender = createMessageSender();

        var recipientId = account.getSelfRecipientId();

        final var unidentifiedAccess = unidentifiedAccessHelper.getAccessFor(recipientId);
        var recipient = resolveSignalServiceAddress(recipientId);
        var transcript = new SentTranscriptMessage(Optional.of(recipient),
                message.getTimestamp(),
                message,
                message.getExpiresInSeconds(),
                Map.of(recipient, unidentifiedAccess.isPresent()),
                false);
        var syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);

        try {
            var startTime = System.currentTimeMillis();
            messageSender.sendMessage(syncMessage, unidentifiedAccess);
            return SendMessageResult.success(recipient,
                    unidentifiedAccess.isPresent(),
                    false,
                    System.currentTimeMillis() - startTime);
        } catch (UntrustedIdentityException e) {
            return SendMessageResult.identityFailure(recipient, e.getIdentityKey());
        }
    }

    private SendMessageResult sendMessage(
            RecipientId recipientId, SignalServiceDataMessage message
    ) throws IOException {
        var messageSender = createMessageSender();

        final var address = resolveSignalServiceAddress(recipientId);
        try {
            try {
                return messageSender.sendMessage(address, unidentifiedAccessHelper.getAccessFor(recipientId), message);
            } catch (UnregisteredUserException e) {
                final var newRecipientId = refreshRegisteredUser(recipientId);
                return messageSender.sendMessage(resolveSignalServiceAddress(newRecipientId),
                        unidentifiedAccessHelper.getAccessFor(newRecipientId),
                        message);
            }
        } catch (UntrustedIdentityException e) {
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        }
    }

    private SendMessageResult sendNullMessage(RecipientId recipientId) throws IOException {
        var messageSender = createMessageSender();

        final var address = resolveSignalServiceAddress(recipientId);
        try {
            try {
                return messageSender.sendNullMessage(address, unidentifiedAccessHelper.getAccessFor(recipientId));
            } catch (UnregisteredUserException e) {
                final var newRecipientId = refreshRegisteredUser(recipientId);
                final var newAddress = resolveSignalServiceAddress(newRecipientId);
                return messageSender.sendNullMessage(newAddress, unidentifiedAccessHelper.getAccessFor(newRecipientId));
            }
        } catch (UntrustedIdentityException e) {
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        }
    }

    private SignalServiceContent decryptMessage(SignalServiceEnvelope envelope) throws InvalidMetadataMessageException, ProtocolInvalidMessageException, ProtocolDuplicateMessageException, ProtocolLegacyMessageException, ProtocolInvalidKeyIdException, InvalidMetadataVersionException, ProtocolInvalidVersionException, ProtocolNoSessionException, ProtocolInvalidKeyException, SelfSendException, UnsupportedDataMessageException, ProtocolUntrustedIdentityException {
        var cipher = new SignalServiceCipher(account.getSelfAddress(),
                account.getSignalProtocolStore(),
                sessionLock,
                certificateValidator);
        return cipher.decrypt(envelope);
    }

    private void handleEndSession(RecipientId recipientId) {
        account.getSessionStore().deleteAllSessions(recipientId);
    }

    private List<HandleAction> handleSignalServiceDataMessage(
            SignalServiceDataMessage message,
            boolean isSync,
            SignalServiceAddress source,
            SignalServiceAddress destination,
            boolean ignoreAttachments
    ) {
        var actions = new ArrayList<HandleAction>();
        if (message.getGroupContext().isPresent()) {
            if (message.getGroupContext().get().getGroupV1().isPresent()) {
                var groupInfo = message.getGroupContext().get().getGroupV1().get();
                var groupId = GroupId.v1(groupInfo.getGroupId());
                var group = getGroup(groupId);
                if (group == null || group instanceof GroupInfoV1) {
                    var groupV1 = (GroupInfoV1) group;
                    switch (groupInfo.getType()) {
                        case UPDATE: {
                            if (groupV1 == null) {
                                groupV1 = new GroupInfoV1(groupId);
                            }

                            if (groupInfo.getAvatar().isPresent()) {
                                var avatar = groupInfo.getAvatar().get();
                                downloadGroupAvatar(avatar, groupV1.getGroupId());
                            }

                            if (groupInfo.getName().isPresent()) {
                                groupV1.name = groupInfo.getName().get();
                            }

                            if (groupInfo.getMembers().isPresent()) {
                                groupV1.addMembers(groupInfo.getMembers()
                                        .get()
                                        .stream()
                                        .map(this::resolveRecipient)
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
                                groupV1.removeMember(resolveRecipient(source));
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
                final var groupContext = message.getGroupContext().get().getGroupV2().get();
                final var groupMasterKey = groupContext.getMasterKey();

                getOrMigrateGroup(groupMasterKey,
                        groupContext.getRevision(),
                        groupContext.hasSignedGroupChange() ? groupContext.getSignedGroupChange() : null);
            }
        }

        final var conversationPartnerAddress = isSync ? destination : source;
        if (conversationPartnerAddress != null && message.isEndSession()) {
            handleEndSession(resolveRecipient(conversationPartnerAddress));
        }
        if (message.isExpirationUpdate() || message.getBody().isPresent()) {
            if (message.getGroupContext().isPresent()) {
                if (message.getGroupContext().get().getGroupV1().isPresent()) {
                    var groupInfo = message.getGroupContext().get().getGroupV1().get();
                    var group = account.getGroupStore().getOrCreateGroupV1(GroupId.v1(groupInfo.getGroupId()));
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
                setExpirationTimer(resolveRecipient(conversationPartnerAddress), message.getExpiresInSeconds());
            }
        }
        if (!ignoreAttachments) {
            if (message.getAttachments().isPresent()) {
                for (var attachment : message.getAttachments().get()) {
                    downloadAttachment(attachment);
                }
            }
            if (message.getSharedContacts().isPresent()) {
                for (var contact : message.getSharedContacts().get()) {
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
            this.account.getProfileStore().storeProfileKey(resolveRecipient(source), profileKey);
        }
        if (message.getPreviews().isPresent()) {
            final var previews = message.getPreviews().get();
            for (var preview : previews) {
                if (preview.getImage().isPresent()) {
                    downloadAttachment(preview.getImage().get());
                }
            }
        }
        if (message.getQuote().isPresent()) {
            final var quote = message.getQuote().get();

            for (var quotedAttachment : quote.getAttachments()) {
                final var thumbnail = quotedAttachment.getThumbnail();
                if (thumbnail != null) {
                    downloadAttachment(thumbnail);
                }
            }
        }
        if (message.getSticker().isPresent()) {
            final var messageSticker = message.getSticker().get();
            final var stickerPackId = StickerPackId.deserialize(messageSticker.getPackId());
            var sticker = account.getStickerStore().getSticker(stickerPackId);
            if (sticker == null) {
                sticker = new Sticker(stickerPackId, messageSticker.getPackKey());
                account.getStickerStore().updateSticker(sticker);
            }
        }
        return actions;
    }

    private GroupInfoV2 getOrMigrateGroup(
            final GroupMasterKey groupMasterKey, final int revision, final byte[] signedGroupChange
    ) {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

        var groupId = GroupUtils.getGroupIdV2(groupSecretParams);
        var groupInfo = getGroup(groupId);
        final GroupInfoV2 groupInfoV2;
        if (groupInfo instanceof GroupInfoV1) {
            // Received a v2 group message for a v1 group, we need to locally migrate the group
            account.getGroupStore().deleteGroupV1(((GroupInfoV1) groupInfo).getGroupId());
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
                group = groupV2Helper.getUpdatedDecryptedGroup(groupInfoV2.getGroup(),
                        signedGroupChange,
                        groupMasterKey);
            }
            if (group == null) {
                group = groupV2Helper.getDecryptedGroup(groupSecretParams);
            }
            if (group != null) {
                storeProfileKeysFromMembers(group);
                final var avatar = group.getAvatar();
                if (avatar != null && !avatar.isEmpty()) {
                    downloadGroupAvatar(groupId, groupSecretParams, avatar);
                }
            }
            groupInfoV2.setGroup(group, this::resolveRecipient);
            account.getGroupStore().updateGroup(groupInfoV2);
        }

        return groupInfoV2;
    }

    private void storeProfileKeysFromMembers(final DecryptedGroup group) {
        for (var member : group.getMembersList()) {
            final var uuid = UuidUtil.parseOrThrow(member.getUuid().toByteArray());
            final var recipientId = account.getRecipientStore().resolveRecipient(uuid);
            try {
                account.getProfileStore()
                        .storeProfileKey(recipientId, new ProfileKey(member.getProfileKey().toByteArray()));
            } catch (InvalidInputException ignored) {
            }
        }
    }

    private void retryFailedReceivedMessages(ReceiveMessageHandler handler, boolean ignoreAttachments) {
        Set<HandleAction> queuedActions = new HashSet<>();
        for (var cachedMessage : account.getMessageCache().getCachedMessages()) {
            var actions = retryFailedReceivedMessage(handler, ignoreAttachments, cachedMessage);
            if (actions != null) {
                queuedActions.addAll(actions);
            }
        }
        for (var action : queuedActions) {
            try {
                action.execute(this);
            } catch (Throwable e) {
                logger.warn("Message action failed.", e);
            }
        }
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
                content = decryptMessage(envelope);
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
            actions = handleMessage(envelope, content, ignoreAttachments);
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

        Set<HandleAction> queuedActions = null;

        final var messagePipe = getOrCreateMessagePipe();

        var hasCaughtUpWithOldMessages = false;

        while (true) {
            SignalServiceEnvelope envelope;
            SignalServiceContent content = null;
            Exception exception = null;
            final CachedMessage[] cachedMessage = {null};
            try {
                var result = messagePipe.readOrEmpty(timeout, unit, envelope1 -> {
                    final var recipientId = envelope1.hasSource()
                            ? resolveRecipient(envelope1.getSourceIdentifier())
                            : null;
                    // store message on disk, before acknowledging receipt to the server
                    cachedMessage[0] = account.getMessageCache().cacheMessage(envelope1, recipientId);
                });
                if (result.isPresent()) {
                    envelope = result.get();
                } else {
                    // Received indicator that server queue is empty
                    hasCaughtUpWithOldMessages = true;

                    if (queuedActions != null) {
                        for (var action : queuedActions) {
                            try {
                                action.execute(this);
                            } catch (Throwable e) {
                                logger.warn("Message action failed.", e);
                            }
                        }
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
                // address/uuid in envelope is sent by server
                resolveRecipientTrusted(envelope.getSourceAddress());
            }
            final var notAGroupMember = isNotAGroupMember(envelope, content);
            if (!envelope.isReceipt()) {
                try {
                    content = decryptMessage(envelope);
                } catch (Exception e) {
                    exception = e;
                }
                if (!envelope.hasSource() && content != null) {
                    // Store uuid if we don't have it already
                    // address/uuid is validated by unidentified sender certificate
                    resolveRecipientTrusted(content.getSender());
                }
                var actions = handleMessage(envelope, content, ignoreAttachments);
                if (exception instanceof ProtocolInvalidMessageException) {
                    final var sender = resolveRecipient(((ProtocolInvalidMessageException) exception).getSender());
                    logger.debug("Received invalid message, queuing renew session action.");
                    actions.add(new RenewSessionAction(sender));
                }
                if (hasCaughtUpWithOldMessages) {
                    for (var action : actions) {
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
            if (isMessageBlocked(envelope, content)) {
                logger.info("Ignoring a message from blocked user/group: {}", envelope.getTimestamp());
            } else if (notAGroupMember) {
                logger.info("Ignoring a message from a non group member: {}", envelope.getTimestamp());
            } else {
                handler.handleMessage(envelope, content, exception);
            }
            if (cachedMessage[0] != null) {
                if (exception instanceof ProtocolUntrustedIdentityException) {
                    final var identifier = ((ProtocolUntrustedIdentityException) exception).getSender();
                    final var recipientId = resolveRecipient(identifier);
                    queuedActions.add(new RetrieveProfileAction(recipientId));
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
        final var recipientId = resolveRecipient(source);
        if (isContactBlocked(recipientId)) {
            return true;
        }

        if (content != null && content.getDataMessage().isPresent()) {
            var message = content.getDataMessage().get();
            if (message.getGroupContext().isPresent()) {
                var groupId = GroupUtils.getGroupId(message.getGroupContext().get());
                var group = getGroup(groupId);
                if (group != null && group.isBlocked()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isContactBlocked(final String identifier) throws InvalidNumberException {
        final var recipientId = canonicalizeAndResolveRecipient(identifier);
        return isContactBlocked(recipientId);
    }

    private boolean isContactBlocked(final RecipientId recipientId) {
        var sourceContact = account.getContactStore().getContact(recipientId);
        return sourceContact != null && sourceContact.isBlocked();
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
            var message = content.getDataMessage().get();
            if (message.getGroupContext().isPresent()) {
                if (message.getGroupContext().get().getGroupV1().isPresent()) {
                    var groupInfo = message.getGroupContext().get().getGroupV1().get();
                    if (groupInfo.getType() == SignalServiceGroup.Type.QUIT) {
                        return false;
                    }
                }
                var groupId = GroupUtils.getGroupId(message.getGroupContext().get());
                var group = getGroup(groupId);
                if (group != null && !group.isMember(resolveRecipient(source))) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<HandleAction> handleMessage(
            SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments
    ) {
        var actions = new ArrayList<HandleAction>();
        if (content != null) {
            final SignalServiceAddress sender;
            if (!envelope.isUnidentifiedSender() && envelope.hasSource()) {
                sender = envelope.getSourceAddress();
            } else {
                sender = content.getSender();
            }

            if (content.getDataMessage().isPresent()) {
                var message = content.getDataMessage().get();

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
                var syncMessage = content.getSyncMessage().get();
                if (syncMessage.getSent().isPresent()) {
                    var message = syncMessage.getSent().get();
                    final var destination = message.getDestination().orNull();
                    actions.addAll(handleSignalServiceDataMessage(message.getMessage(),
                            true,
                            sender,
                            destination,
                            ignoreAttachments));
                }
                if (syncMessage.getRequest().isPresent() && account.isMasterDevice()) {
                    var rm = syncMessage.getRequest().get();
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
                        final var groupsMessage = syncMessage.getGroups().get();
                        try (var attachmentAsStream = retrieveAttachmentAsStream(groupsMessage.asPointer(), tmpFile)) {
                            var s = new DeviceGroupsInputStream(attachmentAsStream);
                            DeviceGroup g;
                            while ((g = s.read()) != null) {
                                var syncGroup = account.getGroupStore().getOrCreateGroupV1(GroupId.v1(g.getId()));
                                if (syncGroup != null) {
                                    if (g.getName().isPresent()) {
                                        syncGroup.name = g.getName().get();
                                    }
                                    syncGroup.addMembers(g.getMembers()
                                            .stream()
                                            .map(this::resolveRecipient)
                                            .collect(Collectors.toSet()));
                                    if (!g.isActive()) {
                                        syncGroup.removeMember(account.getSelfRecipientId());
                                    } else {
                                        // Add ourself to the member set as it's marked as active
                                        syncGroup.addMembers(List.of(account.getSelfRecipientId()));
                                    }
                                    syncGroup.blocked = g.isBlocked();
                                    if (g.getColor().isPresent()) {
                                        syncGroup.color = g.getColor().get();
                                    }

                                    if (g.getAvatar().isPresent()) {
                                        downloadGroupAvatar(g.getAvatar().get(), syncGroup.getGroupId());
                                    }
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
                    final var blockedListMessage = syncMessage.getBlockedList().get();
                    for (var address : blockedListMessage.getAddresses()) {
                        setContactBlocked(resolveRecipient(address), true);
                    }
                    for (var groupId : blockedListMessage.getGroupIds()
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
                        final var contactsMessage = syncMessage.getContacts().get();
                        try (var attachmentAsStream = retrieveAttachmentAsStream(contactsMessage.getContactsStream()
                                .asPointer(), tmpFile)) {
                            var s = new DeviceContactsInputStream(attachmentAsStream);
                            DeviceContact c;
                            while ((c = s.read()) != null) {
                                if (c.getAddress().matches(account.getSelfAddress()) && c.getProfileKey().isPresent()) {
                                    account.setProfileKey(c.getProfileKey().get());
                                }
                                final var recipientId = resolveRecipientTrusted(c.getAddress());
                                var contact = account.getContactStore().getContact(recipientId);
                                final var builder = contact == null
                                        ? Contact.newBuilder()
                                        : Contact.newBuilder(contact);
                                if (c.getName().isPresent()) {
                                    builder.withName(c.getName().get());
                                }
                                if (c.getColor().isPresent()) {
                                    builder.withColor(c.getColor().get());
                                }
                                if (c.getProfileKey().isPresent()) {
                                    account.getProfileStore().storeProfileKey(recipientId, c.getProfileKey().get());
                                }
                                if (c.getVerified().isPresent()) {
                                    final var verifiedMessage = c.getVerified().get();
                                    account.getIdentityKeyStore()
                                            .setIdentityTrustLevel(resolveRecipientTrusted(verifiedMessage.getDestination()),
                                                    verifiedMessage.getIdentityKey(),
                                                    TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
                                }
                                if (c.getExpirationTimer().isPresent()) {
                                    builder.withMessageExpirationTime(c.getExpirationTimer().get());
                                }
                                builder.withBlocked(c.isBlocked());
                                builder.withArchived(c.isArchived());
                                account.getContactStore().storeContact(recipientId, builder.build());

                                if (c.getAvatar().isPresent()) {
                                    downloadContactAvatar(c.getAvatar().get(), c.getAddress());
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
                    final var verifiedMessage = syncMessage.getVerified().get();
                    account.getIdentityKeyStore()
                            .setIdentityTrustLevel(resolveRecipientTrusted(verifiedMessage.getDestination()),
                                    verifiedMessage.getIdentityKey(),
                                    TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
                }
                if (syncMessage.getStickerPackOperations().isPresent()) {
                    final var stickerPackOperationMessages = syncMessage.getStickerPackOperations().get();
                    for (var m : stickerPackOperationMessages) {
                        if (!m.getPackId().isPresent()) {
                            continue;
                        }
                        final var stickerPackId = StickerPackId.deserialize(m.getPackId().get());
                        var sticker = account.getStickerStore().getSticker(stickerPackId);
                        if (sticker == null) {
                            if (!m.getPackKey().isPresent()) {
                                continue;
                            }
                            sticker = new Sticker(stickerPackId, m.getPackKey().get());
                        }
                        sticker.setInstalled(!m.getType().isPresent()
                                || m.getType().get() == StickerPackOperationMessage.Type.INSTALL);
                        account.getStickerStore().updateSticker(sticker);
                    }
                }
                if (syncMessage.getFetchType().isPresent()) {
                    switch (syncMessage.getFetchType().get()) {
                        case LOCAL_PROFILE:
                            getRecipientProfile(account.getSelfRecipientId(), true);
                        case STORAGE_MANIFEST:
                            // TODO
                    }
                }
                if (syncMessage.getKeys().isPresent()) {
                    final var keysMessage = syncMessage.getKeys().get();
                    if (keysMessage.getStorageService().isPresent()) {
                        final var storageKey = keysMessage.getStorageService().get();
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

        var pointer = attachment.asPointer();
        if (pointer.getPreview().isPresent()) {
            final var preview = pointer.getPreview().get();
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
        var groupOperations = groupsV2Operations.forGroup(groupSecretParams);

        var tmpFile = IOUtils.createTempFile();
        try (InputStream input = messageReceiver.retrieveGroupsV2ProfileAvatar(cdnKey,
                tmpFile,
                ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE)) {
            var encryptedData = IOUtils.readFully(input);

            var decryptedData = groupOperations.decryptAvatar(encryptedData);
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
        var tmpFile = IOUtils.createTempFile();
        try (var input = messageReceiver.retrieveProfileAvatar(avatarPath,
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
            var pointer = attachment.asPointer();
            retrieveAttachmentPointer(pointer, outputStream);
        } else {
            var stream = attachment.asStream();
            IOUtils.copyStream(stream.getInputStream(), outputStream);
        }
    }

    private void retrieveAttachmentPointer(
            SignalServiceAttachmentPointer pointer, OutputStream outputStream
    ) throws IOException {
        var tmpFile = IOUtils.createTempFile();
        try (var input = retrieveAttachmentAsStream(pointer, tmpFile)) {
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
        var groupsFile = IOUtils.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(groupsFile)) {
                var out = new DeviceGroupsOutputStream(fos);
                for (var record : getGroups()) {
                    if (record instanceof GroupInfoV1) {
                        var groupInfo = (GroupInfoV1) record;
                        out.write(new DeviceGroup(groupInfo.getGroupId().serialize(),
                                Optional.fromNullable(groupInfo.name),
                                groupInfo.getMembers()
                                        .stream()
                                        .map(this::resolveSignalServiceAddress)
                                        .collect(Collectors.toList()),
                                createGroupAvatarAttachment(groupInfo.getGroupId()),
                                groupInfo.isMember(account.getSelfRecipientId()),
                                Optional.of(groupInfo.messageExpirationTime),
                                Optional.fromNullable(groupInfo.color),
                                groupInfo.blocked,
                                Optional.absent(),
                                groupInfo.archived));
                    }
                }
            }

            if (groupsFile.exists() && groupsFile.length() > 0) {
                try (var groupsFileStream = new FileInputStream(groupsFile)) {
                    var attachmentStream = SignalServiceAttachment.newStreamBuilder()
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
        var contactsFile = IOUtils.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(contactsFile)) {
                var out = new DeviceContactsOutputStream(fos);
                for (var contactPair : account.getContactStore().getContacts()) {
                    final var recipientId = contactPair.first();
                    final var contact = contactPair.second();
                    final var address = resolveSignalServiceAddress(recipientId);

                    var currentIdentity = account.getIdentityKeyStore().getIdentity(recipientId);
                    VerifiedMessage verifiedMessage = null;
                    if (currentIdentity != null) {
                        verifiedMessage = new VerifiedMessage(address,
                                currentIdentity.getIdentityKey(),
                                currentIdentity.getTrustLevel().toVerifiedState(),
                                currentIdentity.getDateAdded().getTime());
                    }

                    var profileKey = account.getProfileStore().getProfileKey(recipientId);
                    out.write(new DeviceContact(address,
                            Optional.fromNullable(contact.getName()),
                            createContactAvatarAttachment(address),
                            Optional.fromNullable(contact.getColor()),
                            Optional.fromNullable(verifiedMessage),
                            Optional.fromNullable(profileKey),
                            contact.isBlocked(),
                            Optional.of(contact.getMessageExpirationTime()),
                            Optional.absent(),
                            contact.isArchived()));
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
                try (var contactsFileStream = new FileInputStream(contactsFile)) {
                    var attachmentStream = SignalServiceAttachment.newStreamBuilder()
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
        var addresses = new ArrayList<SignalServiceAddress>();
        for (var record : account.getContactStore().getContacts()) {
            if (record.second().isBlocked()) {
                addresses.add(resolveSignalServiceAddress(record.first()));
            }
        }
        var groupIds = new ArrayList<byte[]>();
        for (var record : getGroups()) {
            if (record.isBlocked()) {
                groupIds.add(record.getGroupId().serialize());
            }
        }
        sendSyncMessage(SignalServiceSyncMessage.forBlocked(new BlockedListMessage(addresses, groupIds)));
    }

    private void sendVerifiedMessage(
            SignalServiceAddress destination, IdentityKey identityKey, TrustLevel trustLevel
    ) throws IOException, UntrustedIdentityException {
        var verifiedMessage = new VerifiedMessage(destination,
                identityKey,
                trustLevel.toVerifiedState(),
                System.currentTimeMillis());
        sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
    }

    public List<Pair<RecipientId, Contact>> getContacts() {
        return account.getContactStore().getContacts();
    }

    public String getContactOrProfileName(String number) throws InvalidNumberException {
        final var recipientId = canonicalizeAndResolveRecipient(number);
        final var recipient = account.getRecipientStore().getRecipient(recipientId);
        if (recipient == null) {
            return null;
        }

        if (recipient.getContact() != null && !Util.isEmpty(recipient.getContact().getName())) {
            return recipient.getContact().getName();
        }

        if (recipient.getProfile() != null && recipient.getProfile() != null) {
            return recipient.getProfile().getDisplayName();
        }

        return null;
    }

    public GroupInfo getGroup(GroupId groupId) {
        final var group = account.getGroupStore().getGroup(groupId);
        if (group instanceof GroupInfoV2 && ((GroupInfoV2) group).getGroup() == null) {
            final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(((GroupInfoV2) group).getMasterKey());
            ((GroupInfoV2) group).setGroup(groupV2Helper.getDecryptedGroup(groupSecretParams), this::resolveRecipient);
            account.getGroupStore().updateGroup(group);
        }
        return group;
    }

    public List<IdentityInfo> getIdentities() {
        return account.getIdentityKeyStore().getIdentities();
    }

    public List<IdentityInfo> getIdentities(String number) throws InvalidNumberException {
        final var identity = account.getIdentityKeyStore().getIdentity(canonicalizeAndResolveRecipient(number));
        return identity == null ? List.of() : List.of(identity);
    }

    /**
     * Trust this the identity with this fingerprint
     *
     * @param name        username of the identity
     * @param fingerprint Fingerprint
     */
    public boolean trustIdentityVerified(String name, byte[] fingerprint) throws InvalidNumberException {
        var recipientId = canonicalizeAndResolveRecipient(name);
        return trustIdentity(recipientId,
                identityKey -> Arrays.equals(identityKey.serialize(), fingerprint),
                TrustLevel.TRUSTED_VERIFIED);
    }

    /**
     * Trust this the identity with this safety number
     *
     * @param name         username of the identity
     * @param safetyNumber Safety number
     */
    public boolean trustIdentityVerifiedSafetyNumber(String name, String safetyNumber) throws InvalidNumberException {
        var recipientId = canonicalizeAndResolveRecipient(name);
        var address = account.getRecipientStore().resolveServiceAddress(recipientId);
        return trustIdentity(recipientId,
                identityKey -> safetyNumber.equals(computeSafetyNumber(address, identityKey)),
                TrustLevel.TRUSTED_VERIFIED);
    }

    /**
     * Trust all keys of this identity without verification
     *
     * @param name username of the identity
     */
    public boolean trustIdentityAllKeys(String name) throws InvalidNumberException {
        var recipientId = canonicalizeAndResolveRecipient(name);
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
            sendVerifiedMessage(address, identity.getIdentityKey(), trustLevel);
        } catch (IOException | UntrustedIdentityException e) {
            logger.warn("Failed to send verification sync message: {}", e.getMessage());
        }

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

    public RecipientId canonicalizeAndResolveRecipient(String identifier) throws InvalidNumberException {
        var canonicalizedNumber = UuidUtil.isUuid(identifier)
                ? identifier
                : PhoneNumberFormatter.formatNumber(identifier, account.getUsername());

        return resolveRecipient(canonicalizedNumber);
    }

    private RecipientId resolveRecipient(final String identifier) {
        var address = Utils.getSignalServiceAddressFromIdentifier(identifier);

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
