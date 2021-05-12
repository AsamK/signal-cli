package org.asamk.signal.manager.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.TrustLevel;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.storage.contacts.ContactsStore;
import org.asamk.signal.manager.storage.contacts.LegacyJsonContactsStore;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.GroupStore;
import org.asamk.signal.manager.storage.identities.IdentityKeyStore;
import org.asamk.signal.manager.storage.messageCache.MessageCache;
import org.asamk.signal.manager.storage.prekeys.PreKeyStore;
import org.asamk.signal.manager.storage.prekeys.SignedPreKeyStore;
import org.asamk.signal.manager.storage.profiles.LegacyProfileStore;
import org.asamk.signal.manager.storage.profiles.ProfileStore;
import org.asamk.signal.manager.storage.protocol.LegacyJsonSignalProtocolStore;
import org.asamk.signal.manager.storage.protocol.SignalProtocolStore;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.LegacyRecipientStore;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientStore;
import org.asamk.signal.manager.storage.sessions.SessionStore;
import org.asamk.signal.manager.storage.stickers.StickerStore;
import org.asamk.signal.manager.storage.threads.LegacyJsonThreadStore;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class SignalAccount implements Closeable {

    private final static Logger logger = LoggerFactory.getLogger(SignalAccount.class);

    private static final int MINIMUM_STORAGE_VERSION = 1;
    private static final int CURRENT_STORAGE_VERSION = 2;

    private final ObjectMapper jsonProcessor = Utils.createStorageObjectMapper();

    private final FileChannel fileChannel;
    private final FileLock lock;

    private String username;
    private UUID uuid;
    private String encryptedDeviceName;
    private int deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;
    private boolean isMultiDevice = false;
    private String password;
    private String registrationLockPin;
    private MasterKey pinMasterKey;
    private StorageKey storageKey;
    private ProfileKey profileKey;
    private int preKeyIdOffset;
    private int nextSignedPreKeyId;

    private boolean registered = false;

    private SignalProtocolStore signalProtocolStore;
    private PreKeyStore preKeyStore;
    private SignedPreKeyStore signedPreKeyStore;
    private SessionStore sessionStore;
    private IdentityKeyStore identityKeyStore;
    private GroupStore groupStore;
    private GroupStore.Storage groupStoreStorage;
    private RecipientStore recipientStore;
    private StickerStore stickerStore;
    private StickerStore.Storage stickerStoreStorage;

    private MessageCache messageCache;

    private SignalAccount(final FileChannel fileChannel, final FileLock lock) {
        this.fileChannel = fileChannel;
        this.lock = lock;
    }

    public static SignalAccount load(File dataPath, String username, boolean waitForLock) throws IOException {
        final var fileName = getFileName(dataPath, username);
        final var pair = openFileChannel(fileName, waitForLock);
        try {
            var account = new SignalAccount(pair.first(), pair.second());
            account.load(dataPath);
            account.migrateLegacyConfigs();

            if (!username.equals(account.getUsername())) {
                throw new IOException("Username in account file doesn't match expected number: "
                        + account.getUsername());
            }

            return account;
        } catch (Throwable e) {
            pair.second().close();
            pair.first().close();
            throw e;
        }
    }

    public static SignalAccount create(
            File dataPath, String username, IdentityKeyPair identityKey, int registrationId, ProfileKey profileKey
    ) throws IOException {
        IOUtils.createPrivateDirectories(dataPath);
        var fileName = getFileName(dataPath, username);
        if (!fileName.exists()) {
            IOUtils.createPrivateFile(fileName);
        }

        final var pair = openFileChannel(fileName, true);
        var account = new SignalAccount(pair.first(), pair.second());

        account.username = username;
        account.profileKey = profileKey;

        account.initStores(dataPath, identityKey, registrationId);
        account.groupStore = new GroupStore(getGroupCachePath(dataPath, username),
                account.recipientStore::resolveRecipient,
                account::saveGroupStore);
        account.stickerStore = new StickerStore(account::saveStickerStore);

        account.registered = false;

        account.migrateLegacyConfigs();
        account.save();

        return account;
    }

    private void initStores(
            final File dataPath, final IdentityKeyPair identityKey, final int registrationId
    ) throws IOException {
        recipientStore = RecipientStore.load(getRecipientsStoreFile(dataPath, username), this::mergeRecipients);

        preKeyStore = new PreKeyStore(getPreKeysPath(dataPath, username));
        signedPreKeyStore = new SignedPreKeyStore(getSignedPreKeysPath(dataPath, username));
        sessionStore = new SessionStore(getSessionsPath(dataPath, username), recipientStore::resolveRecipient);
        identityKeyStore = new IdentityKeyStore(getIdentitiesPath(dataPath, username),
                recipientStore::resolveRecipient,
                identityKey,
                registrationId);
        signalProtocolStore = new SignalProtocolStore(preKeyStore, signedPreKeyStore, sessionStore, identityKeyStore);

        messageCache = new MessageCache(getMessageCachePath(dataPath, username));
    }

    public static SignalAccount createOrUpdateLinkedAccount(
            File dataPath,
            String username,
            UUID uuid,
            String password,
            String encryptedDeviceName,
            int deviceId,
            IdentityKeyPair identityKey,
            int registrationId,
            ProfileKey profileKey
    ) throws IOException {
        IOUtils.createPrivateDirectories(dataPath);
        var fileName = getFileName(dataPath, username);
        if (!fileName.exists()) {
            return createLinkedAccount(dataPath,
                    username,
                    uuid,
                    password,
                    encryptedDeviceName,
                    deviceId,
                    identityKey,
                    registrationId,
                    profileKey);
        }

        final var account = load(dataPath, username, true);
        account.setProvisioningData(username, uuid, password, encryptedDeviceName, deviceId, profileKey);
        account.recipientStore.resolveRecipientTrusted(account.getSelfAddress());
        account.sessionStore.archiveAllSessions();
        account.clearAllPreKeys();
        return account;
    }

    private void clearAllPreKeys() {
        this.preKeyIdOffset = 0;
        this.nextSignedPreKeyId = 0;
        this.preKeyStore.removeAllPreKeys();
        this.signedPreKeyStore.removeAllSignedPreKeys();
        save();
    }

    private static SignalAccount createLinkedAccount(
            File dataPath,
            String username,
            UUID uuid,
            String password,
            String encryptedDeviceName,
            int deviceId,
            IdentityKeyPair identityKey,
            int registrationId,
            ProfileKey profileKey
    ) throws IOException {
        var fileName = getFileName(dataPath, username);
        IOUtils.createPrivateFile(fileName);

        final var pair = openFileChannel(fileName, true);
        var account = new SignalAccount(pair.first(), pair.second());

        account.setProvisioningData(username, uuid, password, encryptedDeviceName, deviceId, profileKey);

        account.initStores(dataPath, identityKey, registrationId);
        account.groupStore = new GroupStore(getGroupCachePath(dataPath, username),
                account.recipientStore::resolveRecipient,
                account::saveGroupStore);
        account.stickerStore = new StickerStore(account::saveStickerStore);

        account.recipientStore.resolveRecipientTrusted(account.getSelfAddress());
        account.migrateLegacyConfigs();
        account.save();

        return account;
    }

    private void setProvisioningData(
            final String username,
            final UUID uuid,
            final String password,
            final String encryptedDeviceName,
            final int deviceId,
            final ProfileKey profileKey
    ) {
        this.username = username;
        this.uuid = uuid;
        this.password = password;
        this.profileKey = profileKey;
        this.encryptedDeviceName = encryptedDeviceName;
        this.deviceId = deviceId;
        this.registered = true;
        this.isMultiDevice = true;
    }

    private void migrateLegacyConfigs() {
        if (getPassword() == null) {
            setPassword(KeyUtils.createPassword());
        }

        if (getProfileKey() == null && isRegistered()) {
            // Old config file, creating new profile key
            setProfileKey(KeyUtils.createProfileKey());
        }
        // Ensure our profile key is stored in profile store
        getProfileStore().storeProfileKey(getSelfRecipientId(), getProfileKey());
    }

    private void mergeRecipients(RecipientId recipientId, RecipientId toBeMergedRecipientId) {
        sessionStore.mergeRecipients(recipientId, toBeMergedRecipientId);
        identityKeyStore.mergeRecipients(recipientId, toBeMergedRecipientId);
        messageCache.mergeRecipients(recipientId, toBeMergedRecipientId);
        groupStore.mergeRecipients(recipientId, toBeMergedRecipientId);
    }

    public static File getFileName(File dataPath, String username) {
        return new File(dataPath, username);
    }

    private static File getUserPath(final File dataPath, final String username) {
        final var path = new File(dataPath, username + ".d");
        try {
            IOUtils.createPrivateDirectories(path);
        } catch (IOException e) {
            throw new AssertionError("Failed to create user path", e);
        }
        return path;
    }

    private static File getMessageCachePath(File dataPath, String username) {
        return new File(getUserPath(dataPath, username), "msg-cache");
    }

    private static File getGroupCachePath(File dataPath, String username) {
        return new File(getUserPath(dataPath, username), "group-cache");
    }

    private static File getPreKeysPath(File dataPath, String username) {
        return new File(getUserPath(dataPath, username), "pre-keys");
    }

    private static File getSignedPreKeysPath(File dataPath, String username) {
        return new File(getUserPath(dataPath, username), "signed-pre-keys");
    }

    private static File getIdentitiesPath(File dataPath, String username) {
        return new File(getUserPath(dataPath, username), "identities");
    }

    private static File getSessionsPath(File dataPath, String username) {
        return new File(getUserPath(dataPath, username), "sessions");
    }

    private static File getRecipientsStoreFile(File dataPath, String username) {
        return new File(getUserPath(dataPath, username), "recipients-store");
    }

    public static boolean userExists(File dataPath, String username) {
        if (username == null) {
            return false;
        }
        var f = getFileName(dataPath, username);
        return !(!f.exists() || f.isDirectory());
    }

    private void load(File dataPath) throws IOException {
        JsonNode rootNode;
        synchronized (fileChannel) {
            fileChannel.position(0);
            rootNode = jsonProcessor.readTree(Channels.newInputStream(fileChannel));
        }

        if (rootNode.hasNonNull("version")) {
            var accountVersion = rootNode.get("version").asInt(1);
            if (accountVersion > CURRENT_STORAGE_VERSION) {
                throw new IOException("Config file was created by a more recent version!");
            } else if (accountVersion < MINIMUM_STORAGE_VERSION) {
                throw new IOException("Config file was created by a no longer supported older version!");
            }
        }

        username = Utils.getNotNullNode(rootNode, "username").asText();
        password = Utils.getNotNullNode(rootNode, "password").asText();
        registered = Utils.getNotNullNode(rootNode, "registered").asBoolean();
        if (rootNode.hasNonNull("uuid")) {
            try {
                uuid = UUID.fromString(rootNode.get("uuid").asText());
            } catch (IllegalArgumentException e) {
                throw new IOException("Config file contains an invalid uuid, needs to be a valid UUID", e);
            }
        }
        if (rootNode.hasNonNull("deviceName")) {
            encryptedDeviceName = rootNode.get("deviceName").asText();
        }
        if (rootNode.hasNonNull("deviceId")) {
            deviceId = rootNode.get("deviceId").asInt();
        }
        if (rootNode.hasNonNull("isMultiDevice")) {
            isMultiDevice = rootNode.get("isMultiDevice").asBoolean();
        }
        int registrationId = 0;
        if (rootNode.hasNonNull("registrationId")) {
            registrationId = rootNode.get("registrationId").asInt();
        }
        IdentityKeyPair identityKeyPair = null;
        if (rootNode.hasNonNull("identityPrivateKey") && rootNode.hasNonNull("identityKey")) {
            final var publicKeyBytes = Base64.getDecoder().decode(rootNode.get("identityKey").asText());
            final var privateKeyBytes = Base64.getDecoder().decode(rootNode.get("identityPrivateKey").asText());
            identityKeyPair = KeyUtils.getIdentityKeyPair(publicKeyBytes, privateKeyBytes);
        }

        if (rootNode.hasNonNull("registrationLockPin")) {
            registrationLockPin = rootNode.get("registrationLockPin").asText();
        }
        if (rootNode.hasNonNull("pinMasterKey")) {
            pinMasterKey = new MasterKey(Base64.getDecoder().decode(rootNode.get("pinMasterKey").asText()));
        }
        if (rootNode.hasNonNull("storageKey")) {
            storageKey = new StorageKey(Base64.getDecoder().decode(rootNode.get("storageKey").asText()));
        }
        if (rootNode.hasNonNull("preKeyIdOffset")) {
            preKeyIdOffset = rootNode.get("preKeyIdOffset").asInt(0);
        } else {
            preKeyIdOffset = 0;
        }
        if (rootNode.hasNonNull("nextSignedPreKeyId")) {
            nextSignedPreKeyId = rootNode.get("nextSignedPreKeyId").asInt();
        } else {
            nextSignedPreKeyId = 0;
        }
        if (rootNode.hasNonNull("profileKey")) {
            try {
                profileKey = new ProfileKey(Base64.getDecoder().decode(rootNode.get("profileKey").asText()));
            } catch (InvalidInputException e) {
                throw new IOException(
                        "Config file contains an invalid profileKey, needs to be base64 encoded array of 32 bytes",
                        e);
            }
        }

        var migratedLegacyConfig = false;
        final var legacySignalProtocolStore = rootNode.hasNonNull("axolotlStore")
                ? jsonProcessor.convertValue(Utils.getNotNullNode(rootNode, "axolotlStore"),
                LegacyJsonSignalProtocolStore.class)
                : null;
        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacyIdentityKeyStore() != null) {
            identityKeyPair = legacySignalProtocolStore.getLegacyIdentityKeyStore().getIdentityKeyPair();
            registrationId = legacySignalProtocolStore.getLegacyIdentityKeyStore().getLocalRegistrationId();
            migratedLegacyConfig = true;
        }

        initStores(dataPath, identityKeyPair, registrationId);

        migratedLegacyConfig = loadLegacyStores(rootNode, legacySignalProtocolStore) || migratedLegacyConfig;

        if (rootNode.hasNonNull("groupStore")) {
            groupStoreStorage = jsonProcessor.convertValue(rootNode.get("groupStore"), GroupStore.Storage.class);
            groupStore = GroupStore.fromStorage(groupStoreStorage,
                    getGroupCachePath(dataPath, username),
                    recipientStore::resolveRecipient,
                    this::saveGroupStore);
        } else {
            groupStore = new GroupStore(getGroupCachePath(dataPath, username),
                    recipientStore::resolveRecipient,
                    this::saveGroupStore);
        }

        if (rootNode.hasNonNull("stickerStore")) {
            stickerStoreStorage = jsonProcessor.convertValue(rootNode.get("stickerStore"), StickerStore.Storage.class);
            stickerStore = StickerStore.fromStorage(stickerStoreStorage, this::saveStickerStore);
        } else {
            stickerStore = new StickerStore(this::saveStickerStore);
        }

        migratedLegacyConfig = loadLegacyThreadStore(rootNode) || migratedLegacyConfig;

        if (migratedLegacyConfig) {
            save();
        }
    }

    private boolean loadLegacyStores(
            final JsonNode rootNode, final LegacyJsonSignalProtocolStore legacySignalProtocolStore
    ) {
        var migrated = false;
        var legacyRecipientStoreNode = rootNode.get("recipientStore");
        if (legacyRecipientStoreNode != null) {
            logger.debug("Migrating legacy recipient store.");
            var legacyRecipientStore = jsonProcessor.convertValue(legacyRecipientStoreNode, LegacyRecipientStore.class);
            if (legacyRecipientStore != null) {
                recipientStore.resolveRecipientsTrusted(legacyRecipientStore.getAddresses());
            }
            recipientStore.resolveRecipientTrusted(getSelfAddress());
            migrated = true;
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacyPreKeyStore() != null) {
            logger.debug("Migrating legacy pre key store.");
            for (var entry : legacySignalProtocolStore.getLegacyPreKeyStore().getPreKeys().entrySet()) {
                try {
                    preKeyStore.storePreKey(entry.getKey(), new PreKeyRecord(entry.getValue()));
                } catch (IOException e) {
                    logger.warn("Failed to migrate pre key, ignoring", e);
                }
            }
            migrated = true;
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacySignedPreKeyStore() != null) {
            logger.debug("Migrating legacy signed pre key store.");
            for (var entry : legacySignalProtocolStore.getLegacySignedPreKeyStore().getSignedPreKeys().entrySet()) {
                try {
                    signedPreKeyStore.storeSignedPreKey(entry.getKey(), new SignedPreKeyRecord(entry.getValue()));
                } catch (IOException e) {
                    logger.warn("Failed to migrate signed pre key, ignoring", e);
                }
            }
            migrated = true;
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacySessionStore() != null) {
            logger.debug("Migrating legacy session store.");
            for (var session : legacySignalProtocolStore.getLegacySessionStore().getSessions()) {
                try {
                    sessionStore.storeSession(new SignalProtocolAddress(session.address.getIdentifier(),
                            session.deviceId), new SessionRecord(session.sessionRecord));
                } catch (IOException e) {
                    logger.warn("Failed to migrate session, ignoring", e);
                }
            }
            migrated = true;
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacyIdentityKeyStore() != null) {
            logger.debug("Migrating legacy identity session store.");
            for (var identity : legacySignalProtocolStore.getLegacyIdentityKeyStore().getIdentities()) {
                RecipientId recipientId = recipientStore.resolveRecipientTrusted(identity.getAddress());
                identityKeyStore.saveIdentity(recipientId, identity.getIdentityKey(), identity.getDateAdded());
                identityKeyStore.setIdentityTrustLevel(recipientId,
                        identity.getIdentityKey(),
                        identity.getTrustLevel());
            }
            migrated = true;
        }

        if (rootNode.hasNonNull("contactStore")) {
            logger.debug("Migrating legacy contact store.");
            final var contactStoreNode = rootNode.get("contactStore");
            final var contactStore = jsonProcessor.convertValue(contactStoreNode, LegacyJsonContactsStore.class);
            for (var contact : contactStore.getContacts()) {
                final var recipientId = recipientStore.resolveRecipientTrusted(contact.getAddress());
                recipientStore.storeContact(recipientId,
                        new Contact(contact.name,
                                contact.color,
                                contact.messageExpirationTime,
                                contact.blocked,
                                contact.archived));

                // Store profile keys only in profile store
                var profileKeyString = contact.profileKey;
                if (profileKeyString != null) {
                    final ProfileKey profileKey;
                    try {
                        profileKey = new ProfileKey(Base64.getDecoder().decode(profileKeyString));
                        getProfileStore().storeProfileKey(recipientId, profileKey);
                    } catch (InvalidInputException e) {
                        logger.warn("Failed to parse legacy contact profile key: {}", e.getMessage());
                    }
                }
            }
            migrated = true;
        }

        if (rootNode.hasNonNull("profileStore")) {
            logger.debug("Migrating legacy profile store.");
            var profileStoreNode = rootNode.get("profileStore");
            final var legacyProfileStore = jsonProcessor.convertValue(profileStoreNode, LegacyProfileStore.class);
            for (var profileEntry : legacyProfileStore.getProfileEntries()) {
                var recipientId = recipientStore.resolveRecipient(profileEntry.getServiceAddress());
                recipientStore.storeProfileKeyCredential(recipientId, profileEntry.getProfileKeyCredential());
                recipientStore.storeProfileKey(recipientId, profileEntry.getProfileKey());
                final var profile = profileEntry.getProfile();
                if (profile != null) {
                    final var capabilities = new HashSet<Profile.Capability>();
                    if (profile.getCapabilities() != null) {
                        if (profile.getCapabilities().gv1Migration) {
                            capabilities.add(Profile.Capability.gv1Migration);
                        }
                        if (profile.getCapabilities().gv2) {
                            capabilities.add(Profile.Capability.gv2);
                        }
                        if (profile.getCapabilities().storage) {
                            capabilities.add(Profile.Capability.storage);
                        }
                    }
                    final var newProfile = new Profile(profileEntry.getLastUpdateTimestamp(),
                            profile.getGivenName(),
                            profile.getFamilyName(),
                            profile.getAbout(),
                            profile.getAboutEmoji(),
                            profile.isUnrestrictedUnidentifiedAccess()
                                    ? Profile.UnidentifiedAccessMode.UNRESTRICTED
                                    : profile.getUnidentifiedAccess() != null
                                            ? Profile.UnidentifiedAccessMode.ENABLED
                                            : Profile.UnidentifiedAccessMode.DISABLED,
                            capabilities);
                    recipientStore.storeProfile(recipientId, newProfile);
                }
            }
        }

        return migrated;
    }

    private boolean loadLegacyThreadStore(final JsonNode rootNode) {
        var threadStoreNode = rootNode.get("threadStore");
        if (threadStoreNode != null && !threadStoreNode.isNull()) {
            var threadStore = jsonProcessor.convertValue(threadStoreNode, LegacyJsonThreadStore.class);
            // Migrate thread info to group and contact store
            for (var thread : threadStore.getThreads()) {
                if (thread.id == null || thread.id.isEmpty()) {
                    continue;
                }
                try {
                    if (UuidUtil.isUuid(thread.id) || thread.id.startsWith("+")) {
                        final var recipientId = recipientStore.resolveRecipient(thread.id);
                        var contact = recipientStore.getContact(recipientId);
                        if (contact != null) {
                            recipientStore.storeContact(recipientId,
                                    Contact.newBuilder(contact)
                                            .withMessageExpirationTime(thread.messageExpirationTime)
                                            .build());
                        }
                    } else {
                        var groupInfo = groupStore.getGroup(GroupId.fromBase64(thread.id));
                        if (groupInfo instanceof GroupInfoV1) {
                            ((GroupInfoV1) groupInfo).messageExpirationTime = thread.messageExpirationTime;
                            groupStore.updateGroup(groupInfo);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to read legacy thread info: {}", e.getMessage());
                }
            }
            return true;
        }

        return false;
    }

    private void saveStickerStore(StickerStore.Storage storage) {
        this.stickerStoreStorage = storage;
        save();
    }

    private void saveGroupStore(GroupStore.Storage storage) {
        this.groupStoreStorage = storage;
        save();
    }

    private void save() {
        synchronized (fileChannel) {
            var rootNode = jsonProcessor.createObjectNode();
            rootNode.put("version", CURRENT_STORAGE_VERSION)
                    .put("username", username)
                    .put("uuid", uuid == null ? null : uuid.toString())
                    .put("deviceName", encryptedDeviceName)
                    .put("deviceId", deviceId)
                    .put("isMultiDevice", isMultiDevice)
                    .put("password", password)
                    .put("registrationId", identityKeyStore.getLocalRegistrationId())
                    .put("identityPrivateKey",
                            Base64.getEncoder()
                                    .encodeToString(identityKeyStore.getIdentityKeyPair().getPrivateKey().serialize()))
                    .put("identityKey",
                            Base64.getEncoder()
                                    .encodeToString(identityKeyStore.getIdentityKeyPair().getPublicKey().serialize()))
                    .put("registrationLockPin", registrationLockPin)
                    .put("pinMasterKey",
                            pinMasterKey == null ? null : Base64.getEncoder().encodeToString(pinMasterKey.serialize()))
                    .put("storageKey",
                            storageKey == null ? null : Base64.getEncoder().encodeToString(storageKey.serialize()))
                    .put("preKeyIdOffset", preKeyIdOffset)
                    .put("nextSignedPreKeyId", nextSignedPreKeyId)
                    .put("profileKey",
                            profileKey == null ? null : Base64.getEncoder().encodeToString(profileKey.serialize()))
                    .put("registered", registered)
                    .putPOJO("groupStore", groupStoreStorage)
                    .putPOJO("stickerStore", stickerStoreStorage);
            try {
                try (var output = new ByteArrayOutputStream()) {
                    // Write to memory first to prevent corrupting the file in case of serialization errors
                    jsonProcessor.writeValue(output, rootNode);
                    var input = new ByteArrayInputStream(output.toByteArray());
                    fileChannel.position(0);
                    input.transferTo(Channels.newOutputStream(fileChannel));
                    fileChannel.truncate(fileChannel.position());
                    fileChannel.force(false);
                }
            } catch (Exception e) {
                logger.error("Error saving file: {}", e.getMessage());
            }
        }
    }

    private static Pair<FileChannel, FileLock> openFileChannel(File fileName, boolean waitForLock) throws IOException {
        var fileChannel = new RandomAccessFile(fileName, "rw").getChannel();
        var lock = fileChannel.tryLock();
        if (lock == null) {
            if (!waitForLock) {
                logger.debug("Config file is in use by another instance.");
                throw new IOException("Config file is in use by another instance.");
            }
            logger.info("Config file is in use by another instance, waiting…");
            lock = fileChannel.lock();
            logger.info("Config file lock acquired.");
        }
        return new Pair<>(fileChannel, lock);
    }

    public void addPreKeys(List<PreKeyRecord> records) {
        for (var record : records) {
            if (preKeyIdOffset != record.getId()) {
                logger.error("Invalid pre key id {}, expected {}", record.getId(), preKeyIdOffset);
                throw new AssertionError("Invalid pre key id");
            }
            preKeyStore.storePreKey(record.getId(), record);
            preKeyIdOffset = (preKeyIdOffset + 1) % Medium.MAX_VALUE;
        }
        save();
    }

    public void addSignedPreKey(SignedPreKeyRecord record) {
        if (nextSignedPreKeyId != record.getId()) {
            logger.error("Invalid signed pre key id {}, expected {}", record.getId(), nextSignedPreKeyId);
            throw new AssertionError("Invalid signed pre key id");
        }
        signalProtocolStore.storeSignedPreKey(record.getId(), record);
        nextSignedPreKeyId = (nextSignedPreKeyId + 1) % Medium.MAX_VALUE;
        save();
    }

    public SignalProtocolStore getSignalProtocolStore() {
        return signalProtocolStore;
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    public IdentityKeyStore getIdentityKeyStore() {
        return identityKeyStore;
    }

    public GroupStore getGroupStore() {
        return groupStore;
    }

    public ContactsStore getContactStore() {
        return recipientStore;
    }

    public RecipientStore getRecipientStore() {
        return recipientStore;
    }

    public ProfileStore getProfileStore() {
        return recipientStore;
    }

    public StickerStore getStickerStore() {
        return stickerStore;
    }

    public MessageCache getMessageCache() {
        return messageCache;
    }

    public String getUsername() {
        return username;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(final UUID uuid) {
        this.uuid = uuid;
        save();
    }

    public SignalServiceAddress getSelfAddress() {
        return new SignalServiceAddress(uuid, username);
    }

    public RecipientId getSelfRecipientId() {
        return recipientStore.resolveRecipientTrusted(getSelfAddress());
    }

    public String getEncryptedDeviceName() {
        return encryptedDeviceName;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public boolean isMasterDevice() {
        return deviceId == SignalServiceAddress.DEFAULT_DEVICE_ID;
    }

    public IdentityKeyPair getIdentityKeyPair() {
        return signalProtocolStore.getIdentityKeyPair();
    }

    public int getLocalRegistrationId() {
        return signalProtocolStore.getLocalRegistrationId();
    }

    public String getPassword() {
        return password;
    }

    private void setPassword(final String password) {
        this.password = password;
        save();
    }

    public String getRegistrationLockPin() {
        return registrationLockPin;
    }

    public void setRegistrationLockPin(final String registrationLockPin, final MasterKey pinMasterKey) {
        this.registrationLockPin = registrationLockPin;
        this.pinMasterKey = pinMasterKey;
        save();
    }

    public MasterKey getPinMasterKey() {
        return pinMasterKey;
    }

    public StorageKey getStorageKey() {
        if (pinMasterKey != null) {
            return pinMasterKey.deriveStorageServiceKey();
        }
        return storageKey;
    }

    public void setStorageKey(final StorageKey storageKey) {
        if (storageKey.equals(this.storageKey)) {
            return;
        }
        this.storageKey = storageKey;
        save();
    }

    public ProfileKey getProfileKey() {
        return profileKey;
    }

    public void setProfileKey(final ProfileKey profileKey) {
        if (profileKey.equals(this.profileKey)) {
            return;
        }
        this.profileKey = profileKey;
        save();
    }

    public byte[] getSelfUnidentifiedAccessKey() {
        return UnidentifiedAccess.deriveAccessKeyFrom(getProfileKey());
    }

    public int getPreKeyIdOffset() {
        return preKeyIdOffset;
    }

    public int getNextSignedPreKeyId() {
        return nextSignedPreKeyId;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(final boolean registered) {
        this.registered = registered;
        save();
    }

    public boolean isMultiDevice() {
        return isMultiDevice;
    }

    public void setMultiDevice(final boolean multiDevice) {
        if (isMultiDevice == multiDevice) {
            return;
        }
        isMultiDevice = multiDevice;
        save();
    }

    public boolean isUnrestrictedUnidentifiedAccess() {
        // TODO make configurable
        return false;
    }

    public boolean isDiscoverableByPhoneNumber() {
        // TODO make configurable
        return true;
    }

    public boolean isPhoneNumberShared() {
        // TODO make configurable
        return true;
    }

    public void finishRegistration(final UUID uuid, final MasterKey masterKey, final String pin) {
        this.pinMasterKey = masterKey;
        this.encryptedDeviceName = null;
        this.deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;
        this.isMultiDevice = false;
        this.registered = true;
        this.uuid = uuid;
        this.registrationLockPin = pin;
        save();

        getSessionStore().archiveAllSessions();
        final var recipientId = getRecipientStore().resolveRecipientTrusted(getSelfAddress());
        final var publicKey = getIdentityKeyPair().getPublicKey();
        getIdentityKeyStore().saveIdentity(recipientId, publicKey, new Date());
        getIdentityKeyStore().setIdentityTrustLevel(recipientId, publicKey, TrustLevel.TRUSTED_VERIFIED);
    }

    @Override
    public void close() throws IOException {
        synchronized (fileChannel) {
            try {
                lock.close();
            } catch (ClosedChannelException ignored) {
            }
            fileChannel.close();
        }
    }
}
