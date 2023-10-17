package org.asamk.signal.manager.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.Contact;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.Profile;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.configuration.ConfigurationStore;
import org.asamk.signal.manager.storage.configuration.LegacyConfigurationStore;
import org.asamk.signal.manager.storage.contacts.ContactsStore;
import org.asamk.signal.manager.storage.contacts.LegacyJsonContactsStore;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.GroupStore;
import org.asamk.signal.manager.storage.groups.LegacyGroupStore;
import org.asamk.signal.manager.storage.identities.IdentityKeyStore;
import org.asamk.signal.manager.storage.identities.LegacyIdentityKeyStore;
import org.asamk.signal.manager.storage.identities.SignalIdentityKeyStore;
import org.asamk.signal.manager.storage.keyValue.KeyValueEntry;
import org.asamk.signal.manager.storage.keyValue.KeyValueStore;
import org.asamk.signal.manager.storage.messageCache.MessageCache;
import org.asamk.signal.manager.storage.prekeys.KyberPreKeyStore;
import org.asamk.signal.manager.storage.prekeys.LegacyPreKeyStore;
import org.asamk.signal.manager.storage.prekeys.LegacySignedPreKeyStore;
import org.asamk.signal.manager.storage.prekeys.PreKeyStore;
import org.asamk.signal.manager.storage.prekeys.SignedPreKeyStore;
import org.asamk.signal.manager.storage.profiles.LegacyProfileStore;
import org.asamk.signal.manager.storage.profiles.ProfileStore;
import org.asamk.signal.manager.storage.protocol.LegacyJsonSignalProtocolStore;
import org.asamk.signal.manager.storage.protocol.SignalProtocolStore;
import org.asamk.signal.manager.storage.recipients.CdsiStore;
import org.asamk.signal.manager.storage.recipients.LegacyRecipientStore;
import org.asamk.signal.manager.storage.recipients.LegacyRecipientStore2;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientIdCreator;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.storage.recipients.RecipientStore;
import org.asamk.signal.manager.storage.recipients.RecipientTrustedResolver;
import org.asamk.signal.manager.storage.sendLog.MessageSendLogStore;
import org.asamk.signal.manager.storage.senderKeys.LegacySenderKeyRecordStore;
import org.asamk.signal.manager.storage.senderKeys.LegacySenderKeySharedStore;
import org.asamk.signal.manager.storage.senderKeys.SenderKeyStore;
import org.asamk.signal.manager.storage.sessions.LegacySessionStore;
import org.asamk.signal.manager.storage.sessions.SessionStore;
import org.asamk.signal.manager.storage.stickers.LegacyStickerStore;
import org.asamk.signal.manager.storage.stickers.StickerStore;
import org.asamk.signal.manager.storage.threads.LegacyJsonThreadStore;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.KeyHelper;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.account.PreKeyCollection;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.asamk.signal.manager.config.ServiceConfig.PREKEY_MAXIMUM_ID;
import static org.asamk.signal.manager.config.ServiceConfig.getCapabilities;

public class SignalAccount implements Closeable {

    private final static Logger logger = LoggerFactory.getLogger(SignalAccount.class);

    private static final int MINIMUM_STORAGE_VERSION = 1;
    private static final int CURRENT_STORAGE_VERSION = 8;

    private final Object LOCK = new Object();

    private final ObjectMapper jsonProcessor = Utils.createStorageObjectMapper();

    private final FileChannel fileChannel;
    private final FileLock lock;

    private int previousStorageVersion;

    private File dataPath;
    private String accountPath;

    private ServiceEnvironment serviceEnvironment;
    private String number;
    private String username;
    private String encryptedDeviceName;
    private int deviceId = 0;
    private String password;
    private String registrationLockPin;
    private MasterKey pinMasterKey;
    private StorageKey storageKey;
    private ProfileKey profileKey;

    private Settings settings;

    private final KeyValueEntry<String> verificationSessionId = new KeyValueEntry<>("verification-session-id",
            String.class);
    private final KeyValueEntry<String> verificationSessionNumber = new KeyValueEntry<>("verification-session-number",
            String.class);
    private final KeyValueEntry<Long> lastReceiveTimestamp = new KeyValueEntry<>("last-receive-timestamp",
            long.class,
            0L);
    private final KeyValueEntry<byte[]> cdsiToken = new KeyValueEntry<>("cdsi-token", byte[].class);
    private final KeyValueEntry<Long> lastRecipientsRefresh = new KeyValueEntry<>("last-recipients-refresh",
            long.class);
    private final KeyValueEntry<Long> storageManifestVersion = new KeyValueEntry<>("storage-manifest-version",
            long.class,
            -1L);
    private boolean isMultiDevice = false;
    private boolean registered = false;

    private final AccountData<ACI> aciAccountData = new AccountData<>(ServiceIdType.ACI);
    private final AccountData<PNI> pniAccountData = new AccountData<>(ServiceIdType.PNI);
    private IdentityKeyStore identityKeyStore;
    private SenderKeyStore senderKeyStore;
    private GroupStore groupStore;
    private RecipientStore recipientStore;
    private StickerStore stickerStore;
    private ConfigurationStore configurationStore;
    private KeyValueStore keyValueStore;
    private CdsiStore cdsiStore;

    private MessageCache messageCache;
    private MessageSendLogStore messageSendLogStore;

    private AccountDatabase accountDatabase;

    private SignalAccount(final FileChannel fileChannel, final FileLock lock) {
        this.fileChannel = fileChannel;
        this.lock = lock;
    }

    public static SignalAccount load(
            File dataPath, String accountPath, boolean waitForLock, final Settings settings
    ) throws IOException {
        logger.trace("Opening account file");
        final var fileName = getFileName(dataPath, accountPath);
        final var pair = openFileChannel(fileName, waitForLock);
        try {
            var signalAccount = new SignalAccount(pair.first(), pair.second());
            logger.trace("Loading account file");
            signalAccount.load(dataPath, accountPath, settings);
            logger.trace("Migrating legacy parts of account file");
            signalAccount.migrateLegacyConfigs();

            return signalAccount;
        } catch (Throwable e) {
            pair.second().close();
            pair.first().close();
            throw e;
        }
    }

    public static SignalAccount create(
            File dataPath,
            String accountPath,
            String number,
            ServiceEnvironment serviceEnvironment,
            IdentityKeyPair aciIdentityKey,
            IdentityKeyPair pniIdentityKey,
            ProfileKey profileKey,
            final Settings settings
    ) throws IOException {
        IOUtils.createPrivateDirectories(dataPath);
        var fileName = getFileName(dataPath, accountPath);
        if (!fileName.exists()) {
            IOUtils.createPrivateFile(fileName);
        }

        final var pair = openFileChannel(fileName, true);
        var signalAccount = new SignalAccount(pair.first(), pair.second());

        signalAccount.accountPath = accountPath;
        signalAccount.number = number;
        signalAccount.serviceEnvironment = serviceEnvironment;
        signalAccount.profileKey = profileKey;
        signalAccount.password = KeyUtils.createPassword();
        signalAccount.deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;

        signalAccount.dataPath = dataPath;
        signalAccount.aciAccountData.setIdentityKeyPair(aciIdentityKey);
        signalAccount.pniAccountData.setIdentityKeyPair(pniIdentityKey);
        signalAccount.aciAccountData.setLocalRegistrationId(KeyHelper.generateRegistrationId(false));
        signalAccount.pniAccountData.setLocalRegistrationId(KeyHelper.generateRegistrationId(false));
        signalAccount.settings = settings;

        signalAccount.registered = false;

        signalAccount.previousStorageVersion = CURRENT_STORAGE_VERSION;
        signalAccount.migrateLegacyConfigs();
        signalAccount.save();

        return signalAccount;
    }

    public static SignalAccount createLinkedAccount(
            final File dataPath,
            final String accountPath,
            final ServiceEnvironment serviceEnvironment,
            final Settings settings
    ) throws IOException {
        IOUtils.createPrivateDirectories(dataPath);
        var fileName = getFileName(dataPath, accountPath);
        IOUtils.createPrivateFile(fileName);

        final var pair = openFileChannel(fileName, true);
        final var signalAccount = new SignalAccount(pair.first(), pair.second());

        signalAccount.dataPath = dataPath;
        signalAccount.accountPath = accountPath;
        signalAccount.serviceEnvironment = serviceEnvironment;
        signalAccount.aciAccountData.setLocalRegistrationId(KeyHelper.generateRegistrationId(false));
        signalAccount.pniAccountData.setLocalRegistrationId(KeyHelper.generateRegistrationId(false));
        signalAccount.settings = settings;

        signalAccount.previousStorageVersion = CURRENT_STORAGE_VERSION;

        return signalAccount;
    }

    public void setProvisioningData(
            final String number,
            final ACI aci,
            final PNI pni,
            final String password,
            final String encryptedDeviceName,
            final IdentityKeyPair aciIdentity,
            final IdentityKeyPair pniIdentity,
            final ProfileKey profileKey
    ) {
        this.deviceId = 0;
        this.number = number;
        this.aciAccountData.setServiceId(aci);
        this.pniAccountData.setServiceId(pni);
        getRecipientTrustedResolver().resolveSelfRecipientTrusted(getSelfRecipientAddress());
        this.password = password;
        this.profileKey = profileKey;
        getProfileStore().storeSelfProfileKey(getSelfRecipientId(), getProfileKey());
        this.encryptedDeviceName = encryptedDeviceName;
        this.aciAccountData.setIdentityKeyPair(aciIdentity);
        this.pniAccountData.setIdentityKeyPair(pniIdentity);
        this.registered = false;
        this.isMultiDevice = true;
        getKeyValueStore().storeEntry(lastReceiveTimestamp, 0L);
        this.pinMasterKey = null;
        getKeyValueStore().storeEntry(storageManifestVersion, -1L);
        this.setStorageManifest(null);
        this.storageKey = null;
        getSenderKeyStore().deleteAll();
        trustSelfIdentity(ServiceIdType.ACI);
        trustSelfIdentity(ServiceIdType.PNI);
        aciAccountData.getSessionStore().archiveAllSessions();
        pniAccountData.getSessionStore().archiveAllSessions();
        clearAllPreKeys();
        getKeyValueStore().storeEntry(lastRecipientsRefresh, null);
        save();
    }

    public void finishLinking(final int deviceId) {
        this.registered = true;
        this.deviceId = deviceId;
        save();
    }

    public void finishRegistration(
            final ACI aci,
            final PNI pni,
            final MasterKey masterKey,
            final String pin,
            final PreKeyCollection aciPreKeys,
            final PreKeyCollection pniPreKeys
    ) {
        this.pinMasterKey = masterKey;
        getKeyValueStore().storeEntry(storageManifestVersion, -1L);
        this.setStorageManifest(null);
        this.storageKey = null;
        this.encryptedDeviceName = null;
        this.deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;
        this.isMultiDevice = false;
        this.registered = true;
        this.aciAccountData.setServiceId(aci);
        this.pniAccountData.setServiceId(pni);
        this.registrationLockPin = pin;
        getKeyValueStore().storeEntry(lastReceiveTimestamp, 0L);
        save();

        setPreKeys(ServiceIdType.ACI, aciPreKeys);
        setPreKeys(ServiceIdType.PNI, pniPreKeys);
        aciAccountData.getSessionStore().archiveAllSessions();
        pniAccountData.getSessionStore().archiveAllSessions();
        getSenderKeyStore().deleteAll();
        getRecipientTrustedResolver().resolveSelfRecipientTrusted(getSelfRecipientAddress());
        trustSelfIdentity(ServiceIdType.ACI);
        trustSelfIdentity(ServiceIdType.PNI);
        getKeyValueStore().storeEntry(lastRecipientsRefresh, null);
    }

    public void initDatabase() {
        getAccountDatabase();
    }

    private void migrateLegacyConfigs() {
        if (isPrimaryDevice() && getPniIdentityKeyPair() == null) {
            setPniIdentityKeyPair(KeyUtils.generateIdentityKeyPair());
        }
    }

    private void mergeRecipients(
            final Connection connection, RecipientId recipientId, RecipientId toBeMergedRecipientId
    ) throws SQLException {
        getMessageCache().mergeRecipients(recipientId, toBeMergedRecipientId);
        getGroupStore().mergeRecipients(connection, recipientId, toBeMergedRecipientId);
    }

    public void removeRecipient(final RecipientId recipientId) {
        final var recipientAddress = getRecipientStore().resolveRecipientAddress(recipientId);
        if (recipientAddress.matches(getSelfRecipientAddress())) {
            throw new RuntimeException("Can't delete self recipient");
        }
        getRecipientStore().deleteRecipientData(recipientId);
        getMessageCache().deleteMessages(recipientId);
        if (recipientAddress.serviceId().isPresent()) {
            final var serviceId = recipientAddress.serviceId().get();
            aciAccountData.getSessionStore().deleteAllSessions(serviceId);
            pniAccountData.getSessionStore().deleteAllSessions(serviceId);
            getIdentityKeyStore().deleteIdentity(serviceId);
            getSenderKeyStore().deleteAll(serviceId);
        }
    }

    public static File getFileName(File dataPath, String account) {
        return new File(dataPath, account);
    }

    private static File getUserPath(final File dataPath, final String account) {
        final var path = new File(dataPath, account + ".d");
        try {
            IOUtils.createPrivateDirectories(path);
        } catch (IOException e) {
            throw new AssertionError("Failed to create user path", e);
        }
        return path;
    }

    private static File getMessageCachePath(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "msg-cache");
    }

    private static File getStorageManifestFile(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "storage-manifest");
    }

    private static File getDatabaseFile(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "account.db");
    }

    public static boolean accountFileExists(File dataPath, String account) {
        if (account == null) {
            return false;
        }
        var f = getFileName(dataPath, account);
        return f.exists() && !f.isDirectory() && f.length() > 0L;
    }

    private void load(
            File dataPath, String accountPath, final Settings settings
    ) throws IOException {
        this.dataPath = dataPath;
        this.accountPath = accountPath;
        this.settings = settings;
        final JsonNode rootNode;
        synchronized (fileChannel) {
            fileChannel.position(0);
            rootNode = jsonProcessor.readTree(Channels.newInputStream(fileChannel));
        }

        var migratedLegacyConfig = false;

        if (rootNode.hasNonNull("version")) {
            var accountVersion = rootNode.get("version").asInt(1);
            if (accountVersion > CURRENT_STORAGE_VERSION) {
                throw new IOException("Config file was created by a more recent version: " + accountVersion);
            } else if (accountVersion < MINIMUM_STORAGE_VERSION) {
                throw new IOException("Config file was created by a no longer supported older version: "
                        + accountVersion);
            }
            previousStorageVersion = accountVersion;
            if (accountVersion < CURRENT_STORAGE_VERSION) {
                migratedLegacyConfig = true;
            }
        }

        if (previousStorageVersion < 8) {
            final var userPath = getUserPath(dataPath, accountPath);
            loadLegacyFile(userPath, rootNode);
            migratedLegacyConfig = true;
        } else {
            final var storage = jsonProcessor.convertValue(rootNode, Storage.class);
            serviceEnvironment = ServiceEnvironment.valueOf(storage.serviceEnvironment);
            registered = storage.registered;
            number = storage.number;
            username = storage.username;
            encryptedDeviceName = storage.encryptedDeviceName;
            deviceId = storage.deviceId;
            isMultiDevice = storage.isMultiDevice;
            password = storage.password;
            setAccountData(aciAccountData, storage.aciAccountData, ACI::parseOrThrow);
            setAccountData(pniAccountData, storage.pniAccountData, PNI::parseOrThrow);
            registrationLockPin = storage.registrationLockPin;
            final var base64 = Base64.getDecoder();
            if (storage.pinMasterKey != null) {
                pinMasterKey = new MasterKey(base64.decode(storage.pinMasterKey));
            }
            if (storage.storageKey != null) {
                storageKey = new StorageKey(base64.decode(storage.storageKey));
            }
            if (storage.profileKey != null) {
                try {
                    profileKey = new ProfileKey(base64.decode(storage.profileKey));
                } catch (InvalidInputException e) {
                    throw new IOException(
                            "Config file contains an invalid profileKey, needs to be base64 encoded array of 32 bytes",
                            e);
                }
            }

        }

        if (migratedLegacyConfig) {
            save();
        }
    }

    private <SERVICE_ID extends ServiceId> void setAccountData(
            AccountData<SERVICE_ID> accountData,
            Storage.AccountData storage,
            Function<String, SERVICE_ID> serviceIdParser
    ) throws IOException {
        if (storage.serviceId != null) {
            try {
                accountData.setServiceId(serviceIdParser.apply(storage.serviceId));
            } catch (IllegalArgumentException e) {
                throw new IOException("Config file contains an invalid serviceId, needs to be a valid UUID", e);
            }
        }
        accountData.setLocalRegistrationId(storage.registrationId);
        if (storage.identityPrivateKey != null && storage.identityPublicKey != null) {
            final var base64 = Base64.getDecoder();
            final var publicKeyBytes = base64.decode(storage.identityPublicKey);
            final var privateKeyBytes = base64.decode(storage.identityPrivateKey);
            final var keyPair = KeyUtils.getIdentityKeyPair(publicKeyBytes, privateKeyBytes);
            accountData.setIdentityKeyPair(keyPair);
        }
        accountData.preKeyMetadata.nextPreKeyId = storage.nextPreKeyId;
        accountData.preKeyMetadata.nextSignedPreKeyId = storage.nextSignedPreKeyId;
        accountData.preKeyMetadata.activeSignedPreKeyId = storage.activeSignedPreKeyId;
        accountData.preKeyMetadata.nextKyberPreKeyId = storage.nextKyberPreKeyId;
        accountData.preKeyMetadata.activeLastResortKyberPreKeyId = storage.activeLastResortKyberPreKeyId;
    }

    private void loadLegacyFile(final File userPath, final JsonNode rootNode) throws IOException {
        number = Utils.getNotNullNode(rootNode, "username").asText();
        if (rootNode.hasNonNull("password")) {
            password = rootNode.get("password").asText();
        }
        if (password == null) {
            password = KeyUtils.createPassword();
        }

        if (rootNode.hasNonNull("serviceEnvironment")) {
            serviceEnvironment = ServiceEnvironment.valueOf(rootNode.get("serviceEnvironment").asText());
        }
        if (serviceEnvironment == null) {
            serviceEnvironment = ServiceEnvironment.LIVE;
        }
        registered = Utils.getNotNullNode(rootNode, "registered").asBoolean();
        if (rootNode.hasNonNull("usernameIdentifier")) {
            username = rootNode.get("usernameIdentifier").asText();
        }
        if (rootNode.hasNonNull("uuid")) {
            try {
                aciAccountData.setServiceId(ACI.parseOrThrow(rootNode.get("uuid").asText()));
            } catch (IllegalArgumentException e) {
                throw new IOException("Config file contains an invalid aci/uuid, needs to be a valid UUID", e);
            }
        }
        if (rootNode.hasNonNull("pni")) {
            try {
                pniAccountData.setServiceId(PNI.parseOrThrow(rootNode.get("pni").asText()));
            } catch (IllegalArgumentException e) {
                throw new IOException("Config file contains an invalid pni, needs to be a valid UUID", e);
            }
        }
        if (rootNode.hasNonNull("sessionId")) {
            getKeyValueStore().storeEntry(verificationSessionId, rootNode.get("sessionId").asText());
        }
        if (rootNode.hasNonNull("sessionNumber")) {
            getKeyValueStore().storeEntry(verificationSessionNumber, rootNode.get("sessionNumber").asText());
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
        if (rootNode.hasNonNull("lastReceiveTimestamp")) {
            getKeyValueStore().storeEntry(lastReceiveTimestamp, rootNode.get("lastReceiveTimestamp").asLong());
        }
        int registrationId = 0;
        if (rootNode.hasNonNull("registrationId")) {
            registrationId = rootNode.get("registrationId").asInt();
        }
        if (rootNode.hasNonNull("pniRegistrationId")) {
            pniAccountData.setLocalRegistrationId(rootNode.get("pniRegistrationId").asInt());
        } else {
            pniAccountData.setLocalRegistrationId(KeyHelper.generateRegistrationId(false));
        }
        IdentityKeyPair aciIdentityKeyPair = null;
        if (rootNode.hasNonNull("identityPrivateKey") && rootNode.hasNonNull("identityKey")) {
            final var publicKeyBytes = Base64.getDecoder().decode(rootNode.get("identityKey").asText());
            final var privateKeyBytes = Base64.getDecoder().decode(rootNode.get("identityPrivateKey").asText());
            aciIdentityKeyPair = KeyUtils.getIdentityKeyPair(publicKeyBytes, privateKeyBytes);
        }
        if (rootNode.hasNonNull("pniIdentityPrivateKey") && rootNode.hasNonNull("pniIdentityKey")) {
            final var publicKeyBytes = Base64.getDecoder().decode(rootNode.get("pniIdentityKey").asText());
            final var privateKeyBytes = Base64.getDecoder().decode(rootNode.get("pniIdentityPrivateKey").asText());
            pniAccountData.setIdentityKeyPair(KeyUtils.getIdentityKeyPair(publicKeyBytes, privateKeyBytes));
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
        if (rootNode.hasNonNull("storageManifestVersion")) {
            getKeyValueStore().storeEntry(storageManifestVersion, rootNode.get("storageManifestVersion").asLong());
        }
        if (rootNode.hasNonNull("preKeyIdOffset")) {
            aciAccountData.preKeyMetadata.nextPreKeyId = rootNode.get("preKeyIdOffset").asInt(1);
        } else {
            aciAccountData.preKeyMetadata.nextPreKeyId = getRandomPreKeyIdOffset();
        }
        if (rootNode.hasNonNull("nextSignedPreKeyId")) {
            aciAccountData.preKeyMetadata.nextSignedPreKeyId = rootNode.get("nextSignedPreKeyId").asInt(1);
        } else {
            aciAccountData.preKeyMetadata.nextSignedPreKeyId = getRandomPreKeyIdOffset();
        }
        if (rootNode.hasNonNull("activeSignedPreKeyId")) {
            aciAccountData.preKeyMetadata.activeSignedPreKeyId = rootNode.get("activeSignedPreKeyId").asInt(-1);
        } else {
            aciAccountData.preKeyMetadata.activeSignedPreKeyId = -1;
        }
        if (rootNode.hasNonNull("pniPreKeyIdOffset")) {
            pniAccountData.preKeyMetadata.nextPreKeyId = rootNode.get("pniPreKeyIdOffset").asInt(1);
        } else {
            pniAccountData.preKeyMetadata.nextPreKeyId = getRandomPreKeyIdOffset();
        }
        if (rootNode.hasNonNull("pniNextSignedPreKeyId")) {
            pniAccountData.preKeyMetadata.nextSignedPreKeyId = rootNode.get("pniNextSignedPreKeyId").asInt(1);
        } else {
            pniAccountData.preKeyMetadata.nextSignedPreKeyId = getRandomPreKeyIdOffset();
        }
        if (rootNode.hasNonNull("pniActiveSignedPreKeyId")) {
            pniAccountData.preKeyMetadata.activeSignedPreKeyId = rootNode.get("pniActiveSignedPreKeyId").asInt(-1);
        } else {
            pniAccountData.preKeyMetadata.activeSignedPreKeyId = -1;
        }
        if (rootNode.hasNonNull("kyberPreKeyIdOffset")) {
            aciAccountData.preKeyMetadata.nextKyberPreKeyId = rootNode.get("kyberPreKeyIdOffset").asInt(1);
        } else {
            aciAccountData.preKeyMetadata.nextKyberPreKeyId = getRandomPreKeyIdOffset();
        }
        if (rootNode.hasNonNull("activeLastResortKyberPreKeyId")) {
            aciAccountData.preKeyMetadata.activeLastResortKyberPreKeyId = rootNode.get("activeLastResortKyberPreKeyId")
                    .asInt(-1);
        } else {
            aciAccountData.preKeyMetadata.activeLastResortKyberPreKeyId = -1;
        }
        if (rootNode.hasNonNull("pniKyberPreKeyIdOffset")) {
            pniAccountData.preKeyMetadata.nextKyberPreKeyId = rootNode.get("pniKyberPreKeyIdOffset").asInt(1);
        } else {
            pniAccountData.preKeyMetadata.nextKyberPreKeyId = getRandomPreKeyIdOffset();
        }
        if (rootNode.hasNonNull("pniActiveLastResortKyberPreKeyId")) {
            pniAccountData.preKeyMetadata.activeLastResortKyberPreKeyId = rootNode.get(
                    "pniActiveLastResortKyberPreKeyId").asInt(-1);
        } else {
            pniAccountData.preKeyMetadata.activeLastResortKyberPreKeyId = -1;
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
        if (profileKey == null) {
            // Old config file, creating new profile key
            setProfileKey(KeyUtils.createProfileKey());
        }
        getProfileStore().storeProfileKey(getSelfRecipientId(), getProfileKey());

        if (previousStorageVersion < 5) {
            final var legacyRecipientsStoreFile = new File(userPath, "recipients-store");
            if (legacyRecipientsStoreFile.exists()) {
                LegacyRecipientStore2.migrate(legacyRecipientsStoreFile, getRecipientStore());
                // Ensure our profile key is stored in profile store
                getProfileStore().storeSelfProfileKey(getSelfRecipientId(), getProfileKey());
            }
        }
        if (previousStorageVersion < 6) {
            getRecipientTrustedResolver().resolveSelfRecipientTrusted(getSelfRecipientAddress());
        }
        final var legacyAciPreKeysPath = new File(userPath, "pre-keys");
        if (legacyAciPreKeysPath.exists()) {
            LegacyPreKeyStore.migrate(legacyAciPreKeysPath, aciAccountData.getPreKeyStore());
        }
        final var legacyPniPreKeysPath = new File(userPath, "pre-keys-pni");
        if (legacyPniPreKeysPath.exists()) {
            LegacyPreKeyStore.migrate(legacyPniPreKeysPath, pniAccountData.getPreKeyStore());
        }
        final var legacyAciSignedPreKeysPath = new File(userPath, "signed-pre-keys");
        if (legacyAciSignedPreKeysPath.exists()) {
            LegacySignedPreKeyStore.migrate(legacyAciSignedPreKeysPath, aciAccountData.getSignedPreKeyStore());
        }
        final var legacyPniSignedPreKeysPath = new File(userPath, "signed-pre-keys-pni");
        if (legacyPniSignedPreKeysPath.exists()) {
            LegacySignedPreKeyStore.migrate(legacyPniSignedPreKeysPath, pniAccountData.getSignedPreKeyStore());
        }
        final var legacySessionsPath = new File(userPath, "sessions");
        if (legacySessionsPath.exists()) {
            LegacySessionStore.migrate(legacySessionsPath,
                    getRecipientResolver(),
                    getRecipientAddressResolver(),
                    aciAccountData.getSessionStore());
        }
        final var legacyIdentitiesPath = new File(userPath, "identities");
        if (legacyIdentitiesPath.exists()) {
            LegacyIdentityKeyStore.migrate(legacyIdentitiesPath,
                    getRecipientResolver(),
                    getRecipientAddressResolver(),
                    getIdentityKeyStore());
        }
        final var legacySignalProtocolStore = rootNode.hasNonNull("axolotlStore")
                ? jsonProcessor.convertValue(Utils.getNotNullNode(rootNode, "axolotlStore"),
                LegacyJsonSignalProtocolStore.class)
                : null;
        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacyIdentityKeyStore() != null) {
            aciIdentityKeyPair = legacySignalProtocolStore.getLegacyIdentityKeyStore().getIdentityKeyPair();
            registrationId = legacySignalProtocolStore.getLegacyIdentityKeyStore().getLocalRegistrationId();
        }

        this.aciAccountData.setIdentityKeyPair(aciIdentityKeyPair);
        this.aciAccountData.setLocalRegistrationId(registrationId);

        loadLegacyStores(rootNode, legacySignalProtocolStore);

        final var legacySenderKeysPath = new File(userPath, "sender-keys");
        if (legacySenderKeysPath.exists()) {
            LegacySenderKeyRecordStore.migrate(legacySenderKeysPath,
                    getRecipientResolver(),
                    getRecipientAddressResolver(),
                    getSenderKeyStore());
        }
        final var legacySenderKeysSharedPath = new File(userPath, "shared-sender-keys-store");
        if (legacySenderKeysSharedPath.exists()) {
            LegacySenderKeySharedStore.migrate(legacySenderKeysSharedPath,
                    getRecipientResolver(),
                    getRecipientAddressResolver(),
                    getSenderKeyStore());
        }
        if (rootNode.hasNonNull("groupStore")) {
            final var groupStoreStorage = jsonProcessor.convertValue(rootNode.get("groupStore"),
                    LegacyGroupStore.Storage.class);
            LegacyGroupStore.migrate(groupStoreStorage,
                    new File(userPath, "group-cache"),
                    getRecipientResolver(),
                    getGroupStore());
        }

        if (rootNode.hasNonNull("stickerStore")) {
            final var storage = jsonProcessor.convertValue(rootNode.get("stickerStore"),
                    LegacyStickerStore.Storage.class);
            LegacyStickerStore.migrate(storage, getStickerStore());
        }

        if (rootNode.hasNonNull("configurationStore")) {
            final var configurationStoreStorage = jsonProcessor.convertValue(rootNode.get("configurationStore"),
                    LegacyConfigurationStore.Storage.class);
            LegacyConfigurationStore.migrate(configurationStoreStorage, getConfigurationStore());
        }

        loadLegacyThreadStore(rootNode);
    }

    private void loadLegacyStores(
            final JsonNode rootNode, final LegacyJsonSignalProtocolStore legacySignalProtocolStore
    ) {
        var legacyRecipientStoreNode = rootNode.get("recipientStore");
        if (legacyRecipientStoreNode != null) {
            logger.debug("Migrating legacy recipient store.");
            var legacyRecipientStore = jsonProcessor.convertValue(legacyRecipientStoreNode, LegacyRecipientStore.class);
            if (legacyRecipientStore != null) {
                legacyRecipientStore.getAddresses()
                        .forEach(recipient -> getRecipientStore().resolveRecipientTrusted(recipient));
            }
            getRecipientTrustedResolver().resolveSelfRecipientTrusted(getSelfRecipientAddress());
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacyPreKeyStore() != null) {
            logger.debug("Migrating legacy pre key store.");
            for (var entry : legacySignalProtocolStore.getLegacyPreKeyStore().getPreKeys().entrySet()) {
                try {
                    aciAccountData.getPreKeyStore().storePreKey(entry.getKey(), new PreKeyRecord(entry.getValue()));
                } catch (InvalidMessageException e) {
                    logger.warn("Failed to migrate pre key, ignoring", e);
                }
            }
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacySignedPreKeyStore() != null) {
            logger.debug("Migrating legacy signed pre key store.");
            for (var entry : legacySignalProtocolStore.getLegacySignedPreKeyStore().getSignedPreKeys().entrySet()) {
                try {
                    aciAccountData.getSignedPreKeyStore()
                            .storeSignedPreKey(entry.getKey(), new SignedPreKeyRecord(entry.getValue()));
                } catch (InvalidMessageException e) {
                    logger.warn("Failed to migrate signed pre key, ignoring", e);
                }
            }
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacySessionStore() != null) {
            logger.debug("Migrating legacy session store.");
            for (var session : legacySignalProtocolStore.getLegacySessionStore().getSessions()) {
                try {
                    aciAccountData.getSessionStore()
                            .storeSession(new SignalProtocolAddress(session.address.getIdentifier(), session.deviceId),
                                    new SessionRecord(session.sessionRecord));
                } catch (Exception e) {
                    logger.warn("Failed to migrate session, ignoring", e);
                }
            }
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacyIdentityKeyStore() != null) {
            logger.debug("Migrating legacy identity session store.");
            for (var identity : legacySignalProtocolStore.getLegacyIdentityKeyStore().getIdentities()) {
                if (identity.getAddress().serviceId().isEmpty()) {
                    continue;
                }
                final var serviceId = identity.getAddress().serviceId().get();
                getIdentityKeyStore().saveIdentity(serviceId, identity.getIdentityKey());
                getIdentityKeyStore().setIdentityTrustLevel(serviceId,
                        identity.getIdentityKey(),
                        identity.getTrustLevel());
            }
        }

        if (rootNode.hasNonNull("contactStore")) {
            logger.debug("Migrating legacy contact store.");
            final var contactStoreNode = rootNode.get("contactStore");
            final var contactStore = jsonProcessor.convertValue(contactStoreNode, LegacyJsonContactsStore.class);
            for (var contact : contactStore.getContacts()) {
                final var recipientId = getRecipientStore().resolveRecipientTrusted(contact.getAddress());
                getContactStore().storeContact(recipientId,
                        new Contact(contact.name,
                                null,
                                contact.color,
                                contact.messageExpirationTime,
                                contact.blocked,
                                contact.archived,
                                false));

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
        }

        if (rootNode.hasNonNull("profileStore")) {
            logger.debug("Migrating legacy profile store.");
            var profileStoreNode = rootNode.get("profileStore");
            final var legacyProfileStore = jsonProcessor.convertValue(profileStoreNode, LegacyProfileStore.class);
            for (var profileEntry : legacyProfileStore.getProfileEntries()) {
                var recipientId = getRecipientResolver().resolveRecipient(profileEntry.address());
                // Not migrating profile key credential here, it was changed to expiring profile key credentials
                getProfileStore().storeProfileKey(recipientId, profileEntry.profileKey());
                final var profile = profileEntry.profile();
                if (profile != null) {
                    final var capabilities = new HashSet<Profile.Capability>();
                    if (profile.getCapabilities() != null) {
                        if (profile.getCapabilities().gv1Migration) {
                            capabilities.add(Profile.Capability.gv1Migration);
                        }
                        if (profile.getCapabilities().storage) {
                            capabilities.add(Profile.Capability.storage);
                        }
                    }
                    final var newProfile = new Profile(profileEntry.lastUpdateTimestamp(),
                            profile.getGivenName(),
                            profile.getFamilyName(),
                            profile.getAbout(),
                            profile.getAboutEmoji(),
                            null,
                            null,
                            profile.isUnrestrictedUnidentifiedAccess()
                                    ? Profile.UnidentifiedAccessMode.UNRESTRICTED
                                    : profile.getUnidentifiedAccess() != null
                                            ? Profile.UnidentifiedAccessMode.ENABLED
                                            : Profile.UnidentifiedAccessMode.DISABLED,
                            capabilities);
                    getProfileStore().storeProfile(recipientId, newProfile);
                }
            }
        }
    }

    private void loadLegacyThreadStore(final JsonNode rootNode) {
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
                        final var recipientId = getRecipientResolver().resolveRecipient(thread.id);
                        var contact = getContactStore().getContact(recipientId);
                        if (contact != null) {
                            getContactStore().storeContact(recipientId,
                                    Contact.newBuilder(contact)
                                            .withMessageExpirationTime(thread.messageExpirationTime)
                                            .build());
                        }
                    } else {
                        var groupInfo = getGroupStore().getGroup(GroupId.fromBase64(thread.id));
                        if (groupInfo instanceof GroupInfoV1) {
                            ((GroupInfoV1) groupInfo).messageExpirationTime = thread.messageExpirationTime;
                            getGroupStore().updateGroup(groupInfo);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to read legacy thread info: {}", e.getMessage());
                }
            }
        }
    }

    private void save() {
        synchronized (fileChannel) {
            final var base64 = Base64.getEncoder();
            final var storage = new Storage(CURRENT_STORAGE_VERSION,
                    serviceEnvironment.name(),
                    registered,
                    number,
                    username,
                    encryptedDeviceName,
                    deviceId,
                    isMultiDevice,
                    password,
                    Storage.AccountData.from(aciAccountData),
                    Storage.AccountData.from(pniAccountData),
                    registrationLockPin,
                    pinMasterKey == null ? null : base64.encodeToString(pinMasterKey.serialize()),
                    storageKey == null ? null : base64.encodeToString(storageKey.serialize()),
                    profileKey == null ? null : base64.encodeToString(profileKey.serialize()));
            try {
                try (var output = new ByteArrayOutputStream()) {
                    // Write to memory first to prevent corrupting the file in case of serialization errors
                    jsonProcessor.writeValue(output, storage);
                    var input = new ByteArrayInputStream(output.toByteArray());
                    fileChannel.position(0);
                    input.transferTo(Channels.newOutputStream(fileChannel));
                    fileChannel.truncate(fileChannel.position());
                    fileChannel.force(false);
                }
            } catch (Exception e) {
                logger.error("Error saving file: {}", e.getMessage(), e);
            }
        }
    }

    private static Pair<FileChannel, FileLock> openFileChannel(File fileName, boolean waitForLock) throws IOException {
        var fileChannel = new RandomAccessFile(fileName, "rw").getChannel();
        try {
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
            final var result = new Pair<>(fileChannel, lock);
            fileChannel = null;
            return result;
        } finally {
            if (fileChannel != null) {
                fileChannel.close();
            }
        }
    }

    private void clearAllPreKeys() {
        clearAllPreKeys(ServiceIdType.ACI);
        clearAllPreKeys(ServiceIdType.PNI);
    }

    private void clearAllPreKeys(ServiceIdType serviceIdType) {
        final var accountData = getAccountData(serviceIdType);
        resetPreKeyOffsets(serviceIdType);
        resetKyberPreKeyOffsets(serviceIdType);
        accountData.getPreKeyStore().removeAllPreKeys();
        accountData.getSignedPreKeyStore().removeAllSignedPreKeys();
        accountData.getKyberPreKeyStore().removeAllKyberPreKeys();
        save();
    }

    private void setPreKeys(ServiceIdType serviceIdType, PreKeyCollection preKeyCollection) {
        final var accountData = getAccountData(serviceIdType);
        final var preKeyMetadata = accountData.getPreKeyMetadata();
        preKeyMetadata.nextSignedPreKeyId = preKeyCollection.getSignedPreKey().getId();
        preKeyMetadata.nextKyberPreKeyId = preKeyCollection.getLastResortKyberPreKey().getId();

        accountData.getPreKeyStore().removeAllPreKeys();
        accountData.getSignedPreKeyStore().removeAllSignedPreKeys();
        accountData.getKyberPreKeyStore().removeAllKyberPreKeys();

        addSignedPreKey(serviceIdType, preKeyCollection.getSignedPreKey());
        addLastResortKyberPreKey(serviceIdType, preKeyCollection.getLastResortKyberPreKey());

        save();
    }

    public void resetPreKeyOffsets(final ServiceIdType serviceIdType) {
        final var preKeyMetadata = getAccountData(serviceIdType).getPreKeyMetadata();
        preKeyMetadata.nextPreKeyId = getRandomPreKeyIdOffset();
        preKeyMetadata.nextSignedPreKeyId = getRandomPreKeyIdOffset();
        preKeyMetadata.activeSignedPreKeyId = -1;
        save();
    }

    private static int getRandomPreKeyIdOffset() {
        return KeyUtils.getRandomInt(PREKEY_MAXIMUM_ID);
    }

    public void addPreKeys(ServiceIdType serviceIdType, List<PreKeyRecord> records) {
        final var accountData = getAccountData(serviceIdType);
        final var preKeyMetadata = accountData.getPreKeyMetadata();
        logger.debug("Adding {} {} pre keys with offset {}",
                records.size(),
                serviceIdType,
                preKeyMetadata.nextPreKeyId);
        accountData.getSignalServiceAccountDataStore()
                .markAllOneTimeEcPreKeysStaleIfNecessary(System.currentTimeMillis());
        for (var record : records) {
            if (preKeyMetadata.nextPreKeyId != record.getId()) {
                logger.error("Invalid pre key id {}, expected {}", record.getId(), preKeyMetadata.nextPreKeyId);
                throw new AssertionError("Invalid pre key id");
            }
            accountData.getPreKeyStore().storePreKey(record.getId(), record);
            preKeyMetadata.nextPreKeyId = (preKeyMetadata.nextPreKeyId + 1) % PREKEY_MAXIMUM_ID;
        }
        save();
    }

    public void addSignedPreKey(ServiceIdType serviceIdType, SignedPreKeyRecord record) {
        final var accountData = getAccountData(serviceIdType);
        final var preKeyMetadata = accountData.getPreKeyMetadata();
        logger.debug("Adding {} signed pre key with offset {}", serviceIdType, preKeyMetadata.nextSignedPreKeyId);
        if (preKeyMetadata.nextSignedPreKeyId != record.getId()) {
            logger.error("Invalid signed pre key id {}, expected {}",
                    record.getId(),
                    preKeyMetadata.nextSignedPreKeyId);
            throw new AssertionError("Invalid signed pre key id");
        }
        accountData.getSignedPreKeyStore().storeSignedPreKey(record.getId(), record);
        preKeyMetadata.nextSignedPreKeyId = (preKeyMetadata.nextSignedPreKeyId + 1) % PREKEY_MAXIMUM_ID;
        preKeyMetadata.activeSignedPreKeyId = record.getId();
        save();
    }

    public void resetKyberPreKeyOffsets(final ServiceIdType serviceIdType) {
        final var preKeyMetadata = getAccountData(serviceIdType).getPreKeyMetadata();
        preKeyMetadata.nextKyberPreKeyId = getRandomPreKeyIdOffset();
        preKeyMetadata.activeLastResortKyberPreKeyId = -1;
        save();
    }

    public void addKyberPreKeys(ServiceIdType serviceIdType, List<KyberPreKeyRecord> records) {
        final var accountData = getAccountData(serviceIdType);
        final var preKeyMetadata = accountData.getPreKeyMetadata();
        logger.debug("Adding {} {} kyber pre keys with offset {}",
                records.size(),
                serviceIdType,
                preKeyMetadata.nextKyberPreKeyId);
        accountData.getSignalServiceAccountDataStore()
                .markAllOneTimeEcPreKeysStaleIfNecessary(System.currentTimeMillis());
        for (var record : records) {
            if (preKeyMetadata.nextKyberPreKeyId != record.getId()) {
                logger.error("Invalid kyber pre key id {}, expected {}",
                        record.getId(),
                        preKeyMetadata.nextKyberPreKeyId);
                throw new AssertionError("Invalid kyber pre key id");
            }
            accountData.getKyberPreKeyStore().storeKyberPreKey(record.getId(), record);
            preKeyMetadata.nextKyberPreKeyId = (preKeyMetadata.nextKyberPreKeyId + 1) % PREKEY_MAXIMUM_ID;
        }
        save();
    }

    public void addLastResortKyberPreKey(ServiceIdType serviceIdType, KyberPreKeyRecord record) {
        final var accountData = getAccountData(serviceIdType);
        final var preKeyMetadata = accountData.getPreKeyMetadata();
        logger.debug("Adding {} last resort kyber pre key with offset {}",
                serviceIdType,
                preKeyMetadata.nextKyberPreKeyId);
        if (preKeyMetadata.nextKyberPreKeyId != record.getId()) {
            logger.error("Invalid last resort kyber pre key id {}, expected {}",
                    record.getId(),
                    preKeyMetadata.nextKyberPreKeyId);
            throw new AssertionError("Invalid last resort kyber pre key id");
        }
        accountData.getKyberPreKeyStore().storeLastResortKyberPreKey(record.getId(), record);
        preKeyMetadata.activeLastResortKyberPreKeyId = record.getId();
        preKeyMetadata.nextKyberPreKeyId = (preKeyMetadata.nextKyberPreKeyId + 1) % PREKEY_MAXIMUM_ID;
        save();
    }

    public int getPreviousStorageVersion() {
        return previousStorageVersion;
    }

    public AccountData<? extends ServiceId> getAccountData(ServiceIdType serviceIdType) {
        return switch (serviceIdType) {
            case ACI -> aciAccountData;
            case PNI -> pniAccountData;
        };
    }

    public AccountData<? extends ServiceId> getAccountData(ServiceId accountIdentifier) {
        if (accountIdentifier.equals(aciAccountData.getServiceId())) {
            return aciAccountData;
        } else if (accountIdentifier.equals(pniAccountData.getServiceId())) {
            return pniAccountData;
        } else {
            throw new IllegalArgumentException("No matching account data found for " + accountIdentifier);
        }
    }

    public SignalServiceDataStore getSignalServiceDataStore() {
        return new SignalServiceDataStore() {
            @Override
            public SignalServiceAccountDataStore get(final ServiceId accountIdentifier) {
                return getAccountData(accountIdentifier).getSignalServiceAccountDataStore();
            }

            @Override
            public SignalServiceAccountDataStore aci() {
                return aciAccountData.getSignalServiceAccountDataStore();
            }

            @Override
            public SignalServiceAccountDataStore pni() {
                return pniAccountData.getSignalServiceAccountDataStore();
            }

            @Override
            public boolean isMultiDevice() {
                return SignalAccount.this.isMultiDevice();
            }
        };
    }

    public IdentityKeyStore getIdentityKeyStore() {
        return getOrCreate(() -> identityKeyStore,
                () -> identityKeyStore = new IdentityKeyStore(getAccountDatabase(), settings.trustNewIdentity()));
    }

    public GroupStore getGroupStore() {
        return getOrCreate(() -> groupStore,
                () -> groupStore = new GroupStore(getAccountDatabase(),
                        getRecipientResolver(),
                        getRecipientIdCreator()));
    }

    public ContactsStore getContactStore() {
        return getRecipientStore();
    }

    public CdsiStore getCdsiStore() {
        return getOrCreate(() -> cdsiStore, () -> cdsiStore = new CdsiStore(getAccountDatabase()));
    }

    private RecipientIdCreator getRecipientIdCreator() {
        return recipientId -> getRecipientStore().create(recipientId);
    }

    public RecipientResolver getRecipientResolver() {
        return new RecipientResolver.RecipientResolverWrapper(this::getRecipientStore);
    }

    public RecipientTrustedResolver getRecipientTrustedResolver() {
        return new RecipientTrustedResolver.RecipientTrustedResolverWrapper(this::getRecipientStore);
    }

    public RecipientAddressResolver getRecipientAddressResolver() {
        return recipientId -> getRecipientStore().resolveRecipientAddress(recipientId);
    }

    public RecipientStore getRecipientStore() {
        return getOrCreate(() -> recipientStore,
                () -> recipientStore = new RecipientStore(this::mergeRecipients,
                        this::getSelfRecipientAddress,
                        getAccountDatabase()));
    }

    public ProfileStore getProfileStore() {
        return getRecipientStore();
    }

    public StickerStore getStickerStore() {
        return getOrCreate(() -> stickerStore, () -> stickerStore = new StickerStore(getAccountDatabase()));
    }

    public SenderKeyStore getSenderKeyStore() {
        return getOrCreate(() -> senderKeyStore, () -> senderKeyStore = new SenderKeyStore(getAccountDatabase()));
    }

    private KeyValueStore getKeyValueStore() {
        return getOrCreate(() -> keyValueStore, () -> keyValueStore = new KeyValueStore(getAccountDatabase()));
    }

    public ConfigurationStore getConfigurationStore() {
        return getOrCreate(() -> configurationStore,
                () -> configurationStore = new ConfigurationStore(getKeyValueStore()));
    }

    public MessageCache getMessageCache() {
        return getOrCreate(() -> messageCache,
                () -> messageCache = new MessageCache(getMessageCachePath(dataPath, accountPath)));
    }

    public AccountDatabase getAccountDatabase() {
        return getOrCreate(() -> accountDatabase, () -> {
            try {
                accountDatabase = AccountDatabase.init(getDatabaseFile(dataPath, accountPath));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public MessageSendLogStore getMessageSendLogStore() {
        return getOrCreate(() -> messageSendLogStore,
                () -> messageSendLogStore = new MessageSendLogStore(getAccountDatabase(),
                        settings.disableMessageSendLog()));
    }

    public CredentialsProvider getCredentialsProvider() {
        return new CredentialsProvider() {
            @Override
            public ACI getAci() {
                return aciAccountData.getServiceId();
            }

            @Override
            public PNI getPni() {
                return pniAccountData.getServiceId();
            }

            @Override
            public String getE164() {
                return number;
            }

            @Override
            public String getPassword() {
                return password;
            }

            @Override
            public int getDeviceId() {
                return deviceId;
            }
        };
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(final String number) {
        this.number = number;
        save();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
        save();
    }

    public ServiceEnvironment getServiceEnvironment() {
        return serviceEnvironment;
    }

    public void setServiceEnvironment(final ServiceEnvironment serviceEnvironment) {
        this.serviceEnvironment = serviceEnvironment;
        save();
    }

    public AccountAttributes getAccountAttributes(String registrationLock) {
        return new AccountAttributes(null,
                aciAccountData.getLocalRegistrationId(),
                false,
                false,
                true,
                registrationLock != null ? registrationLock : getRegistrationLock(),
                getSelfUnidentifiedAccessKey(),
                isUnrestrictedUnidentifiedAccess(),
                isDiscoverableByPhoneNumber(),
                getAccountCapabilities(),
                encryptedDeviceName,
                pniAccountData.getLocalRegistrationId(),
                null); // TODO recoveryPassword?
    }

    public AccountAttributes.Capabilities getAccountCapabilities() {
        return getCapabilities(isPrimaryDevice());
    }

    public ServiceId getAccountId(ServiceIdType serviceIdType) {
        return getAccountData(serviceIdType).getServiceId();
    }

    public ACI getAci() {
        return aciAccountData.getServiceId();
    }

    public void setAci(final ACI aci) {
        this.aciAccountData.setServiceId(aci);
        save();
    }

    public PNI getPni() {
        return pniAccountData.getServiceId();
    }

    public void setPni(final PNI updatedPni) {
        final var oldPni = pniAccountData.getServiceId();
        if (oldPni != null && !oldPni.equals(updatedPni)) {
            // Clear data for old PNI
            identityKeyStore.deleteIdentity(oldPni);
        }

        this.pniAccountData.setServiceId(updatedPni);
        getRecipientTrustedResolver().resolveSelfRecipientTrusted(getSelfRecipientAddress());
        trustSelfIdentity(ServiceIdType.PNI);
        save();
    }

    public void setNewPniIdentity(
            final IdentityKeyPair pniIdentityKeyPair,
            final SignedPreKeyRecord pniSignedPreKey,
            final KyberPreKeyRecord lastResortKyberPreKey,
            final int localPniRegistrationId
    ) {
        setPniIdentityKeyPair(pniIdentityKeyPair);
        pniAccountData.setLocalRegistrationId(localPniRegistrationId);

        final AccountData<? extends ServiceId> accountData = getAccountData(ServiceIdType.PNI);
        final var preKeyMetadata = accountData.getPreKeyMetadata();
        preKeyMetadata.nextSignedPreKeyId = pniSignedPreKey.getId();
        accountData.getSignedPreKeyStore().removeSignedPreKey(pniSignedPreKey.getId());
        addSignedPreKey(ServiceIdType.PNI, pniSignedPreKey);
        if (lastResortKyberPreKey != null) {
            preKeyMetadata.nextKyberPreKeyId = lastResortKyberPreKey.getId();
            accountData.getKyberPreKeyStore().removeKyberPreKey(lastResortKyberPreKey.getId());
            addLastResortKyberPreKey(ServiceIdType.PNI, lastResortKyberPreKey);
        }
        save();
    }

    public SignalServiceAddress getSelfAddress() {
        return new SignalServiceAddress(getAci(), number);
    }

    public RecipientAddress getSelfRecipientAddress() {
        return new RecipientAddress(getAci(), getPni(), number, username);
    }

    public RecipientId getSelfRecipientId() {
        return getRecipientResolver().resolveRecipient(getSelfRecipientAddress());
    }

    public String getSessionId(final String forNumber) {
        final var keyValueStore = getKeyValueStore();
        final var sessionNumber = keyValueStore.getEntry(verificationSessionNumber);
        if (!forNumber.equals(sessionNumber)) {
            return null;
        }
        return keyValueStore.getEntry(verificationSessionId);
    }

    public void setSessionId(final String sessionNumber, final String sessionId) {
        final var keyValueStore = getKeyValueStore();
        keyValueStore.storeEntry(verificationSessionNumber, sessionNumber);
        keyValueStore.storeEntry(verificationSessionId, sessionId);
    }

    public void setEncryptedDeviceName(final String encryptedDeviceName) {
        this.encryptedDeviceName = encryptedDeviceName;
        save();
    }

    public int getDeviceId() {
        return deviceId;
    }

    public boolean isPrimaryDevice() {
        return deviceId == SignalServiceAddress.DEFAULT_DEVICE_ID;
    }

    public IdentityKeyPair getIdentityKeyPair(ServiceIdType serviceIdType) {
        return getAccountData(serviceIdType).getIdentityKeyPair();
    }

    public IdentityKeyPair getAciIdentityKeyPair() {
        return aciAccountData.getIdentityKeyPair();
    }

    public IdentityKeyPair getPniIdentityKeyPair() {
        return pniAccountData.getIdentityKeyPair();
    }

    public void setPniIdentityKeyPair(final IdentityKeyPair identityKeyPair) {
        pniAccountData.setIdentityKeyPair(identityKeyPair);
        trustSelfIdentity(ServiceIdType.PNI);
        save();
    }

    public String getPassword() {
        return password;
    }

    public void setRegistrationLockPin(final String registrationLockPin) {
        this.registrationLockPin = registrationLockPin;
        save();
    }

    public String getRegistrationLockPin() {
        return registrationLockPin;
    }

    public String getRegistrationLock() {
        final var masterKey = getPinBackedMasterKey();
        if (masterKey == null) {
            return null;
        }
        return masterKey.deriveRegistrationLock();
    }

    public MasterKey getPinBackedMasterKey() {
        if (registrationLockPin == null) {
            return null;
        }
        return pinMasterKey;
    }

    public MasterKey getOrCreatePinMasterKey() {
        if (pinMasterKey == null) {
            pinMasterKey = KeyUtils.createMasterKey();
            save();
        }
        return pinMasterKey;
    }

    public StorageKey getStorageKey() {
        if (pinMasterKey != null) {
            return pinMasterKey.deriveStorageServiceKey();
        }
        return storageKey;
    }

    public StorageKey getOrCreateStorageKey() {
        if (isPrimaryDevice()) {
            return getOrCreatePinMasterKey().deriveStorageServiceKey();
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

    public long getStorageManifestVersion() {
        return getKeyValueStore().getEntry(storageManifestVersion);
    }

    public void setStorageManifestVersion(final long value) {
        getKeyValueStore().storeEntry(storageManifestVersion, value);
    }

    public Optional<SignalStorageManifest> getStorageManifest() {
        final var storageManifestFile = getStorageManifestFile(dataPath, accountPath);
        if (!storageManifestFile.exists()) {
            return Optional.empty();
        }
        try (var inputStream = new FileInputStream(storageManifestFile)) {
            return Optional.of(SignalStorageManifest.deserialize(inputStream.readAllBytes()));
        } catch (IOException e) {
            logger.warn("Failed to read local storage manifest.", e);
            return Optional.empty();
        }
    }

    public void setStorageManifest(SignalStorageManifest manifest) {
        final var storageManifestFile = getStorageManifestFile(dataPath, accountPath);
        if (manifest == null) {
            if (storageManifestFile.exists()) {
                try {
                    Files.delete(storageManifestFile.toPath());
                } catch (IOException e) {
                    logger.error("Failed to delete local storage manifest.", e);
                }
            }
            return;
        }

        final var manifestBytes = manifest.serialize();
        try (var outputStream = new FileOutputStream(storageManifestFile)) {
            outputStream.write(manifestBytes);
        } catch (IOException e) {
            logger.error("Failed to store local storage manifest.", e);
        }
    }

    public byte[] getCdsiToken() {
        return getKeyValueStore().getEntry(cdsiToken);
    }

    public void setCdsiToken(final byte[] value) {
        getKeyValueStore().storeEntry(cdsiToken, value);
    }

    public Long getLastRecipientsRefresh() {
        return getKeyValueStore().getEntry(lastRecipientsRefresh);
    }

    public void setLastRecipientsRefresh(final Long value) {
        getKeyValueStore().storeEntry(lastRecipientsRefresh, value);
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
        getProfileStore().storeSelfProfileKey(getSelfRecipientId(), getProfileKey());
    }

    public byte[] getSelfUnidentifiedAccessKey() {
        return UnidentifiedAccess.deriveAccessKeyFrom(getProfileKey());
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

    public long getLastReceiveTimestamp() {
        return getKeyValueStore().getEntry(lastReceiveTimestamp);
    }

    public void setLastReceiveTimestamp(final long value) {
        getKeyValueStore().storeEntry(lastReceiveTimestamp, value);
    }

    public boolean isUnrestrictedUnidentifiedAccess() {
        final var profile = getProfileStore().getProfile(getSelfRecipientId());
        return profile != null && profile.getUnidentifiedAccessMode() == Profile.UnidentifiedAccessMode.UNRESTRICTED;
    }

    public boolean isDiscoverableByPhoneNumber() {
        final var phoneNumberUnlisted = getConfigurationStore().getPhoneNumberUnlisted();
        return phoneNumberUnlisted == null || !phoneNumberUnlisted;
    }

    private void trustSelfIdentity(ServiceIdType serviceIdType) {
        final var accountData = getAccountData(serviceIdType);
        final var serviceId = accountData.getServiceId();
        final var identityKeyPair = accountData.getIdentityKeyPair();
        if (serviceId == null || identityKeyPair == null) {
            return;
        }
        final var publicKey = identityKeyPair.getPublicKey();
        getIdentityKeyStore().saveIdentity(serviceId, publicKey);
        getIdentityKeyStore().setIdentityTrustLevel(serviceId, publicKey, TrustLevel.TRUSTED_VERIFIED);
    }

    public void deleteAccountData() throws IOException {
        close();
        try (final var files = Files.walk(getUserPath(dataPath, accountPath).toPath())
                .sorted(Comparator.reverseOrder())) {
            for (final var file = files.iterator(); file.hasNext(); ) {
                Files.delete(file.next());
            }
        }
        Files.delete(getFileName(dataPath, accountPath).toPath());
    }

    @Override
    public void close() {
        synchronized (fileChannel) {
            if (accountDatabase != null) {
                accountDatabase.close();
            }
            if (messageSendLogStore != null) {
                messageSendLogStore.close();
            }
            try {
                try {
                    lock.close();
                } catch (ClosedChannelException ignored) {
                }
                fileChannel.close();
            } catch (IOException e) {
                logger.warn("Failed to close account: {}", e.getMessage(), e);
            }
        }
    }

    private <T> T getOrCreate(Supplier<T> supplier, Callable creator) {
        var value = supplier.get();
        if (value != null) {
            return value;
        }

        synchronized (LOCK) {
            value = supplier.get();
            if (value != null) {
                return value;
            }
            creator.call();
            return supplier.get();
        }
    }

    private interface Callable {

        void call();
    }

    public static class PreKeyMetadata {

        private int nextPreKeyId = 1;
        private int nextSignedPreKeyId = 1;
        private int activeSignedPreKeyId = -1;
        private int nextKyberPreKeyId = 1;
        private int activeLastResortKyberPreKeyId = -1;

        public int getNextPreKeyId() {
            return nextPreKeyId;
        }

        public int getNextSignedPreKeyId() {
            return nextSignedPreKeyId;
        }

        public int getActiveSignedPreKeyId() {
            return activeSignedPreKeyId;
        }

        public int getNextKyberPreKeyId() {
            return nextKyberPreKeyId;
        }

        public int getActiveLastResortKyberPreKeyId() {
            return activeLastResortKyberPreKeyId;
        }
    }

    public class AccountData<SERVICE_ID extends ServiceId> {

        private final ServiceIdType serviceIdType;
        private SERVICE_ID serviceId;
        private IdentityKeyPair identityKeyPair;
        private int localRegistrationId;
        private final PreKeyMetadata preKeyMetadata = new PreKeyMetadata();

        private SignalProtocolStore signalProtocolStore;
        private PreKeyStore preKeyStore;
        private SignedPreKeyStore signedPreKeyStore;
        private KyberPreKeyStore kyberPreKeyStore;
        private SessionStore sessionStore;
        private SignalIdentityKeyStore identityKeyStore;

        private AccountData(final ServiceIdType serviceIdType) {
            this.serviceIdType = serviceIdType;
        }

        public SERVICE_ID getServiceId() {
            return serviceId;
        }

        private void setServiceId(final SERVICE_ID serviceId) {
            this.serviceId = serviceId;
        }

        public IdentityKeyPair getIdentityKeyPair() {
            return identityKeyPair;
        }

        private void setIdentityKeyPair(final IdentityKeyPair identityKeyPair) {
            this.identityKeyPair = identityKeyPair;
        }

        public int getLocalRegistrationId() {
            return localRegistrationId;
        }

        private void setLocalRegistrationId(final int localRegistrationId) {
            this.localRegistrationId = localRegistrationId;
            this.identityKeyStore = null;
        }

        public PreKeyMetadata getPreKeyMetadata() {
            return preKeyMetadata;
        }

        private SignalServiceAccountDataStore getSignalServiceAccountDataStore() {
            return getOrCreate(() -> signalProtocolStore,
                    () -> signalProtocolStore = new SignalProtocolStore(getPreKeyStore(),
                            getSignedPreKeyStore(),
                            getKyberPreKeyStore(),
                            getSessionStore(),
                            getIdentityKeyStore(),
                            getSenderKeyStore(),
                            SignalAccount.this::isMultiDevice));
        }

        public PreKeyStore getPreKeyStore() {
            return getOrCreate(() -> preKeyStore,
                    () -> preKeyStore = new PreKeyStore(getAccountDatabase(), serviceIdType));
        }

        public SignedPreKeyStore getSignedPreKeyStore() {
            return getOrCreate(() -> signedPreKeyStore,
                    () -> signedPreKeyStore = new SignedPreKeyStore(getAccountDatabase(), serviceIdType));
        }

        public KyberPreKeyStore getKyberPreKeyStore() {
            return getOrCreate(() -> kyberPreKeyStore,
                    () -> kyberPreKeyStore = new KyberPreKeyStore(getAccountDatabase(), serviceIdType));
        }

        public SessionStore getSessionStore() {
            return getOrCreate(() -> sessionStore,
                    () -> sessionStore = new SessionStore(getAccountDatabase(), serviceIdType));
        }

        public SignalIdentityKeyStore getIdentityKeyStore() {
            return getOrCreate(() -> identityKeyStore,
                    () -> identityKeyStore = new SignalIdentityKeyStore(() -> identityKeyPair,
                            localRegistrationId,
                            SignalAccount.this.getIdentityKeyStore()));
        }
    }

    public record Storage(
            int version,
            String serviceEnvironment,
            boolean registered,
            String number,
            String username,
            String encryptedDeviceName,
            int deviceId,
            boolean isMultiDevice,
            String password,
            AccountData aciAccountData,
            AccountData pniAccountData,
            String registrationLockPin,
            String pinMasterKey,
            String storageKey,
            String profileKey
    ) {

        public record AccountData(
                String serviceId,
                int registrationId,
                String identityPrivateKey,
                String identityPublicKey,

                int nextPreKeyId,
                int nextSignedPreKeyId,
                int activeSignedPreKeyId,
                int nextKyberPreKeyId,
                int activeLastResortKyberPreKeyId
        ) {

            private static AccountData from(final SignalAccount.AccountData<?> accountData) {
                final var base64 = Base64.getEncoder();
                final var preKeyMetadata = accountData.getPreKeyMetadata();
                return new AccountData(accountData.getServiceId() == null
                        ? null
                        : accountData.getServiceId().toString(),
                        accountData.getLocalRegistrationId(),
                        accountData.getIdentityKeyPair() == null
                                ? null
                                : base64.encodeToString(accountData.getIdentityKeyPair().getPrivateKey().serialize()),
                        accountData.getIdentityKeyPair() == null
                                ? null
                                : base64.encodeToString(accountData.getIdentityKeyPair().getPublicKey().serialize()),
                        preKeyMetadata.getNextPreKeyId(),
                        preKeyMetadata.getNextSignedPreKeyId(),
                        preKeyMetadata.getActiveSignedPreKeyId(),
                        preKeyMetadata.getNextKyberPreKeyId(),
                        preKeyMetadata.getActiveLastResortKyberPreKeyId());
            }
        }
    }
}
