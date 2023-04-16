package org.asamk.signal.manager.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.configuration.ConfigurationStore;
import org.asamk.signal.manager.storage.contacts.ContactsStore;
import org.asamk.signal.manager.storage.contacts.LegacyJsonContactsStore;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.GroupStore;
import org.asamk.signal.manager.storage.groups.LegacyGroupStore;
import org.asamk.signal.manager.storage.identities.IdentityKeyStore;
import org.asamk.signal.manager.storage.identities.LegacyIdentityKeyStore;
import org.asamk.signal.manager.storage.identities.SignalIdentityKeyStore;
import org.asamk.signal.manager.storage.messageCache.MessageCache;
import org.asamk.signal.manager.storage.prekeys.LegacyPreKeyStore;
import org.asamk.signal.manager.storage.prekeys.LegacySignedPreKeyStore;
import org.asamk.signal.manager.storage.prekeys.PreKeyStore;
import org.asamk.signal.manager.storage.prekeys.SignedPreKeyStore;
import org.asamk.signal.manager.storage.profiles.LegacyProfileStore;
import org.asamk.signal.manager.storage.profiles.ProfileStore;
import org.asamk.signal.manager.storage.protocol.LegacyJsonSignalProtocolStore;
import org.asamk.signal.manager.storage.protocol.SignalProtocolStore;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.LegacyRecipientStore;
import org.asamk.signal.manager.storage.recipients.LegacyRecipientStore2;
import org.asamk.signal.manager.storage.recipients.Profile;
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
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.KeyHelper;
import org.signal.libsignal.protocol.util.Medium;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
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
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.asamk.signal.manager.config.ServiceConfig.getCapabilities;

public class SignalAccount implements Closeable {

    private final static Logger logger = LoggerFactory.getLogger(SignalAccount.class);

    private static final int MINIMUM_STORAGE_VERSION = 1;
    private static final int CURRENT_STORAGE_VERSION = 6;

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
    private ACI aci;
    private PNI pni;
    private String sessionId;
    private String sessionNumber;
    private String encryptedDeviceName;
    private int deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;
    private boolean isMultiDevice = false;
    private String password;
    private String registrationLockPin;
    private MasterKey pinMasterKey;
    private StorageKey storageKey;
    private long storageManifestVersion = -1;
    private ProfileKey profileKey;
    private int aciPreKeyIdOffset = 1;
    private int aciNextSignedPreKeyId = 1;
    private int pniPreKeyIdOffset = 1;
    private int pniNextSignedPreKeyId = 1;
    private IdentityKeyPair aciIdentityKeyPair;
    private IdentityKeyPair pniIdentityKeyPair;
    private int localRegistrationId;
    private int localPniRegistrationId;
    private Settings settings;
    private long lastReceiveTimestamp = 0;

    private boolean registered = false;

    private SignalProtocolStore aciSignalProtocolStore;
    private SignalProtocolStore pniSignalProtocolStore;
    private PreKeyStore aciPreKeyStore;
    private SignedPreKeyStore aciSignedPreKeyStore;
    private PreKeyStore pniPreKeyStore;
    private SignedPreKeyStore pniSignedPreKeyStore;
    private SessionStore aciSessionStore;
    private SessionStore pniSessionStore;
    private IdentityKeyStore identityKeyStore;
    private SignalIdentityKeyStore aciIdentityKeyStore;
    private SignalIdentityKeyStore pniIdentityKeyStore;
    private SenderKeyStore senderKeyStore;
    private GroupStore groupStore;
    private RecipientStore recipientStore;
    private StickerStore stickerStore;
    private ConfigurationStore configurationStore;
    private ConfigurationStore.Storage configurationStoreStorage;

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
            int registrationId,
            int pniRegistrationId,
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

        signalAccount.dataPath = dataPath;
        signalAccount.aciIdentityKeyPair = aciIdentityKey;
        signalAccount.pniIdentityKeyPair = pniIdentityKey;
        signalAccount.localRegistrationId = registrationId;
        signalAccount.localPniRegistrationId = pniRegistrationId;
        signalAccount.settings = settings;
        signalAccount.configurationStore = new ConfigurationStore(signalAccount::saveConfigurationStore);

        signalAccount.registered = false;

        signalAccount.previousStorageVersion = CURRENT_STORAGE_VERSION;
        signalAccount.migrateLegacyConfigs();
        signalAccount.save();

        return signalAccount;
    }

    public static SignalAccount createOrUpdateLinkedAccount(
            File dataPath,
            String accountPath,
            String number,
            ServiceEnvironment serviceEnvironment,
            ACI aci,
            PNI pni,
            String password,
            String encryptedDeviceName,
            int deviceId,
            IdentityKeyPair aciIdentityKey,
            IdentityKeyPair pniIdentityKey,
            int registrationId,
            int pniRegistrationId,
            ProfileKey profileKey,
            final Settings settings
    ) throws IOException {
        IOUtils.createPrivateDirectories(dataPath);
        var fileName = getFileName(dataPath, accountPath);
        if (!fileName.exists()) {
            return createLinkedAccount(dataPath,
                    accountPath,
                    number,
                    serviceEnvironment,
                    aci,
                    pni,
                    password,
                    encryptedDeviceName,
                    deviceId,
                    aciIdentityKey,
                    pniIdentityKey,
                    registrationId,
                    pniRegistrationId,
                    profileKey,
                    settings);
        }

        final var signalAccount = load(dataPath, accountPath, true, settings);
        signalAccount.setProvisioningData(number,
                aci,
                pni,
                password,
                encryptedDeviceName,
                deviceId,
                aciIdentityKey,
                pniIdentityKey,
                profileKey);
        signalAccount.getRecipientTrustedResolver()
                .resolveSelfRecipientTrusted(signalAccount.getSelfRecipientAddress());
        signalAccount.getAciSessionStore().archiveAllSessions();
        signalAccount.getPniSessionStore().archiveAllSessions();
        signalAccount.getSenderKeyStore().deleteAll();
        signalAccount.clearAllPreKeys();
        return signalAccount;
    }

    public void initDatabase() {
        getAccountDatabase();
    }

    private void clearAllPreKeys() {
        resetPreKeyOffsets(ServiceIdType.ACI);
        resetPreKeyOffsets(ServiceIdType.PNI);
        this.getAciPreKeyStore().removeAllPreKeys();
        this.getAciSignedPreKeyStore().removeAllSignedPreKeys();
        this.getPniPreKeyStore().removeAllPreKeys();
        this.getPniSignedPreKeyStore().removeAllSignedPreKeys();
        save();
    }

    private static SignalAccount createLinkedAccount(
            File dataPath,
            String accountPath,
            String number,
            ServiceEnvironment serviceEnvironment,
            ACI aci,
            PNI pni,
            String password,
            String encryptedDeviceName,
            int deviceId,
            IdentityKeyPair aciIdentityKey,
            IdentityKeyPair pniIdentityKey,
            int registrationId,
            int pniRegistrationId,
            ProfileKey profileKey,
            final Settings settings
    ) throws IOException {
        var fileName = getFileName(dataPath, accountPath);
        IOUtils.createPrivateFile(fileName);

        final var pair = openFileChannel(fileName, true);
        var signalAccount = new SignalAccount(pair.first(), pair.second());

        signalAccount.dataPath = dataPath;
        signalAccount.accountPath = accountPath;
        signalAccount.serviceEnvironment = serviceEnvironment;
        signalAccount.localRegistrationId = registrationId;
        signalAccount.localPniRegistrationId = pniRegistrationId;
        signalAccount.settings = settings;
        signalAccount.setProvisioningData(number,
                aci,
                pni,
                password,
                encryptedDeviceName,
                deviceId,
                aciIdentityKey,
                pniIdentityKey,
                profileKey);

        signalAccount.configurationStore = new ConfigurationStore(signalAccount::saveConfigurationStore);

        signalAccount.getRecipientTrustedResolver()
                .resolveSelfRecipientTrusted(signalAccount.getSelfRecipientAddress());
        signalAccount.previousStorageVersion = CURRENT_STORAGE_VERSION;
        signalAccount.migrateLegacyConfigs();
        signalAccount.clearAllPreKeys();
        signalAccount.save();

        return signalAccount;
    }

    private void setProvisioningData(
            final String number,
            final ACI aci,
            final PNI pni,
            final String password,
            final String encryptedDeviceName,
            final int deviceId,
            final IdentityKeyPair aciIdentity,
            final IdentityKeyPair pniIdentity,
            final ProfileKey profileKey
    ) {
        this.number = number;
        this.aci = aci;
        this.pni = pni;
        this.password = password;
        this.profileKey = profileKey;
        getProfileStore().storeSelfProfileKey(getSelfRecipientId(), getProfileKey());
        this.encryptedDeviceName = encryptedDeviceName;
        this.deviceId = deviceId;
        this.aciIdentityKeyPair = aciIdentity;
        this.pniIdentityKeyPair = pniIdentity;
        this.registered = true;
        this.isMultiDevice = true;
        this.lastReceiveTimestamp = 0;
        this.pinMasterKey = null;
        this.storageManifestVersion = -1;
        this.setStorageManifest(null);
        this.storageKey = null;
        final var aciPublicKey = getAciIdentityKeyPair().getPublicKey();
        getIdentityKeyStore().saveIdentity(getAci(), aciPublicKey);
        getIdentityKeyStore().setIdentityTrustLevel(getAci(), aciPublicKey, TrustLevel.TRUSTED_VERIFIED);
        if (getPniIdentityKeyPair() != null) {
            final var pniPublicKey = getPniIdentityKeyPair().getPublicKey();
            getIdentityKeyStore().saveIdentity(getPni(), pniPublicKey);
            getIdentityKeyStore().setIdentityTrustLevel(getPni(), pniPublicKey, TrustLevel.TRUSTED_VERIFIED);
        }
    }

    private void migrateLegacyConfigs() {
        if (getPassword() == null) {
            setPassword(KeyUtils.createPassword());
        }

        if (getProfileKey() == null) {
            // Old config file, creating new profile key
            setProfileKey(KeyUtils.createProfileKey());
        }
        getProfileStore().storeProfileKey(getSelfRecipientId(), getProfileKey());
        if (isPrimaryDevice() && getPniIdentityKeyPair() == null && getPni() != null) {
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
        getRecipientStore().deleteRecipientData(recipientId);
        getMessageCache().deleteMessages(recipientId);
        if (recipientAddress.serviceId().isPresent()) {
            final var serviceId = recipientAddress.serviceId().get();
            getAciSessionStore().deleteAllSessions(serviceId);
            getPniSessionStore().deleteAllSessions(serviceId);
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

    private static File getGroupCachePath(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "group-cache");
    }

    private static File getAciPreKeysPath(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "pre-keys");
    }

    private static File getAciSignedPreKeysPath(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "signed-pre-keys");
    }

    private static File getPniPreKeysPath(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "pre-keys-pni");
    }

    private static File getPniSignedPreKeysPath(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "signed-pre-keys-pni");
    }

    private static File getIdentitiesPath(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "identities");
    }

    private static File getSessionsPath(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "sessions");
    }

    private static File getSenderKeysPath(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "sender-keys");
    }

    private static File getSharedSenderKeysFile(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "shared-sender-keys-store");
    }

    private static File getRecipientsStoreFile(File dataPath, String account) {
        return new File(getUserPath(dataPath, account), "recipients-store");
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
        return !(!f.exists() || f.isDirectory());
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

        number = Utils.getNotNullNode(rootNode, "username").asText();
        if (rootNode.hasNonNull("password")) {
            password = rootNode.get("password").asText();
        }
        if (rootNode.hasNonNull("serviceEnvironment")) {
            serviceEnvironment = ServiceEnvironment.valueOf(rootNode.get("serviceEnvironment").asText());
        }
        registered = Utils.getNotNullNode(rootNode, "registered").asBoolean();
        if (rootNode.hasNonNull("usernameIdentifier")) {
            username = rootNode.get("usernameIdentifier").asText();
        }
        if (rootNode.hasNonNull("uuid")) {
            try {
                aci = ACI.parseOrThrow(rootNode.get("uuid").asText());
            } catch (IllegalArgumentException e) {
                throw new IOException("Config file contains an invalid aci/uuid, needs to be a valid UUID", e);
            }
        }
        if (rootNode.hasNonNull("pni")) {
            try {
                pni = PNI.parseOrThrow(rootNode.get("pni").asText());
            } catch (IllegalArgumentException e) {
                throw new IOException("Config file contains an invalid pni, needs to be a valid UUID", e);
            }
        }
        if (rootNode.hasNonNull("sessionId")) {
            sessionId = rootNode.get("sessionId").asText();
        }
        if (rootNode.hasNonNull("sessionNumber")) {
            sessionNumber = rootNode.get("sessionNumber").asText();
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
            lastReceiveTimestamp = rootNode.get("lastReceiveTimestamp").asLong();
        }
        int registrationId = 0;
        if (rootNode.hasNonNull("registrationId")) {
            registrationId = rootNode.get("registrationId").asInt();
        }
        if (rootNode.hasNonNull("pniRegistrationId")) {
            localPniRegistrationId = rootNode.get("pniRegistrationId").asInt();
        } else {
            localPniRegistrationId = KeyHelper.generateRegistrationId(false);
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
            pniIdentityKeyPair = KeyUtils.getIdentityKeyPair(publicKeyBytes, privateKeyBytes);
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
            storageManifestVersion = rootNode.get("storageManifestVersion").asLong();
        }
        if (rootNode.hasNonNull("preKeyIdOffset")) {
            aciPreKeyIdOffset = rootNode.get("preKeyIdOffset").asInt(1);
        } else {
            aciPreKeyIdOffset = 1;
        }
        if (rootNode.hasNonNull("nextSignedPreKeyId")) {
            aciNextSignedPreKeyId = rootNode.get("nextSignedPreKeyId").asInt(1);
        } else {
            aciNextSignedPreKeyId = 1;
        }
        if (rootNode.hasNonNull("pniPreKeyIdOffset")) {
            pniPreKeyIdOffset = rootNode.get("pniPreKeyIdOffset").asInt(1);
        } else {
            pniPreKeyIdOffset = 1;
        }
        if (rootNode.hasNonNull("pniNextSignedPreKeyId")) {
            pniNextSignedPreKeyId = rootNode.get("pniNextSignedPreKeyId").asInt(1);
        } else {
            pniNextSignedPreKeyId = 1;
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

        if (previousStorageVersion < 5) {
            final var legacyRecipientsStoreFile = getRecipientsStoreFile(dataPath, accountPath);
            if (legacyRecipientsStoreFile.exists()) {
                LegacyRecipientStore2.migrate(legacyRecipientsStoreFile, getRecipientStore());
                // Ensure our profile key is stored in profile store
                getProfileStore().storeSelfProfileKey(getSelfRecipientId(), getProfileKey());
                migratedLegacyConfig = true;
            }
        }
        if (previousStorageVersion < 6) {
            getRecipientTrustedResolver().resolveSelfRecipientTrusted(getSelfRecipientAddress());
        }
        final var legacyAciPreKeysPath = getAciPreKeysPath(dataPath, accountPath);
        if (legacyAciPreKeysPath.exists()) {
            LegacyPreKeyStore.migrate(legacyAciPreKeysPath, getAciPreKeyStore());
            migratedLegacyConfig = true;
        }
        final var legacyPniPreKeysPath = getPniPreKeysPath(dataPath, accountPath);
        if (legacyPniPreKeysPath.exists()) {
            LegacyPreKeyStore.migrate(legacyPniPreKeysPath, getPniPreKeyStore());
            migratedLegacyConfig = true;
        }
        final var legacyAciSignedPreKeysPath = getAciSignedPreKeysPath(dataPath, accountPath);
        if (legacyAciSignedPreKeysPath.exists()) {
            LegacySignedPreKeyStore.migrate(legacyAciSignedPreKeysPath, getAciSignedPreKeyStore());
            migratedLegacyConfig = true;
        }
        final var legacyPniSignedPreKeysPath = getPniSignedPreKeysPath(dataPath, accountPath);
        if (legacyPniSignedPreKeysPath.exists()) {
            LegacySignedPreKeyStore.migrate(legacyPniSignedPreKeysPath, getPniSignedPreKeyStore());
            migratedLegacyConfig = true;
        }
        final var legacySessionsPath = getSessionsPath(dataPath, accountPath);
        if (legacySessionsPath.exists()) {
            LegacySessionStore.migrate(legacySessionsPath,
                    getRecipientResolver(),
                    getRecipientAddressResolver(),
                    getAciSessionStore());
            migratedLegacyConfig = true;
        }
        final var legacyIdentitiesPath = getIdentitiesPath(dataPath, accountPath);
        if (legacyIdentitiesPath.exists()) {
            LegacyIdentityKeyStore.migrate(legacyIdentitiesPath,
                    getRecipientResolver(),
                    getRecipientAddressResolver(),
                    getIdentityKeyStore());
            migratedLegacyConfig = true;
        }
        final var legacySignalProtocolStore = rootNode.hasNonNull("axolotlStore")
                ? jsonProcessor.convertValue(Utils.getNotNullNode(rootNode, "axolotlStore"),
                LegacyJsonSignalProtocolStore.class)
                : null;
        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacyIdentityKeyStore() != null) {
            aciIdentityKeyPair = legacySignalProtocolStore.getLegacyIdentityKeyStore().getIdentityKeyPair();
            registrationId = legacySignalProtocolStore.getLegacyIdentityKeyStore().getLocalRegistrationId();
            migratedLegacyConfig = true;
        }

        this.aciIdentityKeyPair = aciIdentityKeyPair;
        this.localRegistrationId = registrationId;

        migratedLegacyConfig = loadLegacyStores(rootNode, legacySignalProtocolStore) || migratedLegacyConfig;

        final var legacySenderKeysPath = getSenderKeysPath(dataPath, accountPath);
        if (legacySenderKeysPath.exists()) {
            LegacySenderKeyRecordStore.migrate(legacySenderKeysPath,
                    getRecipientResolver(),
                    getRecipientAddressResolver(),
                    getSenderKeyStore());
            migratedLegacyConfig = true;
        }
        final var legacySenderKeysSharedPath = getSharedSenderKeysFile(dataPath, accountPath);
        if (legacySenderKeysSharedPath.exists()) {
            LegacySenderKeySharedStore.migrate(legacySenderKeysSharedPath,
                    getRecipientResolver(),
                    getRecipientAddressResolver(),
                    getSenderKeyStore());
            migratedLegacyConfig = true;
        }
        if (rootNode.hasNonNull("groupStore")) {
            final var groupStoreStorage = jsonProcessor.convertValue(rootNode.get("groupStore"),
                    LegacyGroupStore.Storage.class);
            LegacyGroupStore.migrate(groupStoreStorage,
                    getGroupCachePath(dataPath, accountPath),
                    getRecipientResolver(),
                    getGroupStore());
            migratedLegacyConfig = true;
        }

        if (rootNode.hasNonNull("stickerStore")) {
            final var storage = jsonProcessor.convertValue(rootNode.get("stickerStore"),
                    LegacyStickerStore.Storage.class);
            LegacyStickerStore.migrate(storage, getStickerStore());
            migratedLegacyConfig = true;
        }

        if (rootNode.hasNonNull("configurationStore")) {
            configurationStoreStorage = jsonProcessor.convertValue(rootNode.get("configurationStore"),
                    ConfigurationStore.Storage.class);
            configurationStore = ConfigurationStore.fromStorage(configurationStoreStorage,
                    this::saveConfigurationStore);
        } else {
            configurationStore = new ConfigurationStore(this::saveConfigurationStore);
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
                legacyRecipientStore.getAddresses()
                        .forEach(recipient -> getRecipientStore().resolveRecipientTrusted(recipient));
            }
            getRecipientTrustedResolver().resolveSelfRecipientTrusted(getSelfRecipientAddress());
            migrated = true;
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacyPreKeyStore() != null) {
            logger.debug("Migrating legacy pre key store.");
            for (var entry : legacySignalProtocolStore.getLegacyPreKeyStore().getPreKeys().entrySet()) {
                try {
                    getAciPreKeyStore().storePreKey(entry.getKey(), new PreKeyRecord(entry.getValue()));
                } catch (InvalidMessageException e) {
                    logger.warn("Failed to migrate pre key, ignoring", e);
                }
            }
            migrated = true;
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacySignedPreKeyStore() != null) {
            logger.debug("Migrating legacy signed pre key store.");
            for (var entry : legacySignalProtocolStore.getLegacySignedPreKeyStore().getSignedPreKeys().entrySet()) {
                try {
                    getAciSignedPreKeyStore().storeSignedPreKey(entry.getKey(),
                            new SignedPreKeyRecord(entry.getValue()));
                } catch (InvalidMessageException e) {
                    logger.warn("Failed to migrate signed pre key, ignoring", e);
                }
            }
            migrated = true;
        }

        if (legacySignalProtocolStore != null && legacySignalProtocolStore.getLegacySessionStore() != null) {
            logger.debug("Migrating legacy session store.");
            for (var session : legacySignalProtocolStore.getLegacySessionStore().getSessions()) {
                try {
                    getAciSessionStore().storeSession(new SignalProtocolAddress(session.address.getIdentifier(),
                            session.deviceId), new SessionRecord(session.sessionRecord));
                } catch (Exception e) {
                    logger.warn("Failed to migrate session, ignoring", e);
                }
            }
            migrated = true;
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
            migrated = true;
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
            migrated = true;
        }

        if (rootNode.hasNonNull("profileStore")) {
            logger.debug("Migrating legacy profile store.");
            var profileStoreNode = rootNode.get("profileStore");
            final var legacyProfileStore = jsonProcessor.convertValue(profileStoreNode, LegacyProfileStore.class);
            for (var profileEntry : legacyProfileStore.getProfileEntries()) {
                var recipientId = getRecipientResolver().resolveRecipient(profileEntry.getAddress());
                // Not migrating profile key credential here, it was changed to expiring profile key credentials
                getProfileStore().storeProfileKey(recipientId, profileEntry.getProfileKey());
                final var profile = profileEntry.getProfile();
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
                    final var newProfile = new Profile(profileEntry.getLastUpdateTimestamp(),
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
            return true;
        }

        return false;
    }

    private void saveConfigurationStore(ConfigurationStore.Storage storage) {
        this.configurationStoreStorage = storage;
        save();
    }

    private void save() {
        synchronized (fileChannel) {
            var rootNode = jsonProcessor.createObjectNode();
            rootNode.put("version", CURRENT_STORAGE_VERSION)
                    .put("username", number)
                    .put("serviceEnvironment", serviceEnvironment == null ? null : serviceEnvironment.name())
                    .put("usernameIdentifier", username)
                    .put("uuid", aci == null ? null : aci.toString())
                    .put("pni", pni == null ? null : pni.toString())
                    .put("sessionId", sessionId)
                    .put("sessionNumber", sessionNumber)
                    .put("deviceName", encryptedDeviceName)
                    .put("deviceId", deviceId)
                    .put("isMultiDevice", isMultiDevice)
                    .put("lastReceiveTimestamp", lastReceiveTimestamp)
                    .put("password", password)
                    .put("registrationId", localRegistrationId)
                    .put("pniRegistrationId", localPniRegistrationId)
                    .put("identityPrivateKey",
                            Base64.getEncoder().encodeToString(aciIdentityKeyPair.getPrivateKey().serialize()))
                    .put("identityKey",
                            Base64.getEncoder().encodeToString(aciIdentityKeyPair.getPublicKey().serialize()))
                    .put("pniIdentityPrivateKey",
                            pniIdentityKeyPair == null
                                    ? null
                                    : Base64.getEncoder()
                                            .encodeToString(pniIdentityKeyPair.getPrivateKey().serialize()))
                    .put("pniIdentityKey",
                            pniIdentityKeyPair == null
                                    ? null
                                    : Base64.getEncoder().encodeToString(pniIdentityKeyPair.getPublicKey().serialize()))
                    .put("registrationLockPin", registrationLockPin)
                    .put("pinMasterKey",
                            pinMasterKey == null ? null : Base64.getEncoder().encodeToString(pinMasterKey.serialize()))
                    .put("storageKey",
                            storageKey == null ? null : Base64.getEncoder().encodeToString(storageKey.serialize()))
                    .put("storageManifestVersion", storageManifestVersion == -1 ? null : storageManifestVersion)
                    .put("preKeyIdOffset", aciPreKeyIdOffset)
                    .put("nextSignedPreKeyId", aciNextSignedPreKeyId)
                    .put("pniPreKeyIdOffset", pniPreKeyIdOffset)
                    .put("pniNextSignedPreKeyId", pniNextSignedPreKeyId)
                    .put("profileKey",
                            profileKey == null ? null : Base64.getEncoder().encodeToString(profileKey.serialize()))
                    .put("registered", registered)
                    .putPOJO("configurationStore", configurationStoreStorage);
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

    public void resetPreKeyOffsets(final ServiceIdType serviceIdType) {
        if (serviceIdType.equals(ServiceIdType.ACI)) {
            this.aciPreKeyIdOffset = new SecureRandom().nextInt(Medium.MAX_VALUE);
            this.aciNextSignedPreKeyId = new SecureRandom().nextInt(Medium.MAX_VALUE);
        } else {
            this.pniPreKeyIdOffset = new SecureRandom().nextInt(Medium.MAX_VALUE);
            this.pniNextSignedPreKeyId = new SecureRandom().nextInt(Medium.MAX_VALUE);
        }
        save();
    }

    public void addPreKeys(ServiceIdType serviceIdType, List<PreKeyRecord> records) {
        if (serviceIdType.equals(ServiceIdType.ACI)) {
            addAciPreKeys(records);
        } else {
            addPniPreKeys(records);
        }
    }

    public void addAciPreKeys(List<PreKeyRecord> records) {
        for (var record : records) {
            if (aciPreKeyIdOffset != record.getId()) {
                logger.error("Invalid pre key id {}, expected {}", record.getId(), aciPreKeyIdOffset);
                throw new AssertionError("Invalid pre key id");
            }
            getAciPreKeyStore().storePreKey(record.getId(), record);
            aciPreKeyIdOffset = (aciPreKeyIdOffset + 1) % Medium.MAX_VALUE;
        }
        save();
    }

    public void addPniPreKeys(List<PreKeyRecord> records) {
        for (var record : records) {
            if (pniPreKeyIdOffset != record.getId()) {
                logger.error("Invalid pre key id {}, expected {}", record.getId(), pniPreKeyIdOffset);
                throw new AssertionError("Invalid pre key id");
            }
            getPniPreKeyStore().storePreKey(record.getId(), record);
            pniPreKeyIdOffset = (pniPreKeyIdOffset + 1) % Medium.MAX_VALUE;
        }
        save();
    }

    public void addSignedPreKey(ServiceIdType serviceIdType, SignedPreKeyRecord record) {
        if (serviceIdType.equals(ServiceIdType.ACI)) {
            addAciSignedPreKey(record);
        } else {
            addPniSignedPreKey(record);
        }
    }

    public void addAciSignedPreKey(SignedPreKeyRecord record) {
        if (aciNextSignedPreKeyId != record.getId()) {
            logger.error("Invalid signed pre key id {}, expected {}", record.getId(), aciNextSignedPreKeyId);
            throw new AssertionError("Invalid signed pre key id");
        }
        getAciSignedPreKeyStore().storeSignedPreKey(record.getId(), record);
        aciNextSignedPreKeyId = (aciNextSignedPreKeyId + 1) % Medium.MAX_VALUE;
        save();
    }

    public void addPniSignedPreKey(SignedPreKeyRecord record) {
        if (pniNextSignedPreKeyId != record.getId()) {
            logger.error("Invalid signed pre key id {}, expected {}", record.getId(), pniNextSignedPreKeyId);
            throw new AssertionError("Invalid signed pre key id");
        }
        getPniSignedPreKeyStore().storeSignedPreKey(record.getId(), record);
        pniNextSignedPreKeyId = (pniNextSignedPreKeyId + 1) % Medium.MAX_VALUE;
        save();
    }

    public int getPreviousStorageVersion() {
        return previousStorageVersion;
    }

    public SignalServiceDataStore getSignalServiceDataStore() {
        return new SignalServiceDataStore() {
            @Override
            public SignalServiceAccountDataStore get(final ServiceId accountIdentifier) {
                if (accountIdentifier.equals(aci)) {
                    return aci();
                } else if (accountIdentifier.equals(pni)) {
                    return pni();
                } else {
                    throw new IllegalArgumentException("No matching store found for " + accountIdentifier);
                }
            }

            @Override
            public SignalServiceAccountDataStore aci() {
                return getAciSignalServiceAccountDataStore();
            }

            @Override
            public SignalServiceAccountDataStore pni() {
                return getPniSignalServiceAccountDataStore();
            }

            @Override
            public boolean isMultiDevice() {
                return SignalAccount.this.isMultiDevice();
            }
        };
    }

    private SignalServiceAccountDataStore getAciSignalServiceAccountDataStore() {
        return getOrCreate(() -> aciSignalProtocolStore,
                () -> aciSignalProtocolStore = new SignalProtocolStore(getAciPreKeyStore(),
                        getAciSignedPreKeyStore(),
                        getAciSessionStore(),
                        getAciIdentityKeyStore(),
                        getSenderKeyStore(),
                        this::isMultiDevice));
    }

    private SignalServiceAccountDataStore getPniSignalServiceAccountDataStore() {
        return getOrCreate(() -> pniSignalProtocolStore,
                () -> pniSignalProtocolStore = new SignalProtocolStore(getPniPreKeyStore(),
                        getPniSignedPreKeyStore(),
                        getPniSessionStore(),
                        getPniIdentityKeyStore(),
                        getSenderKeyStore(),
                        this::isMultiDevice));
    }

    private PreKeyStore getAciPreKeyStore() {
        return getOrCreate(() -> aciPreKeyStore,
                () -> aciPreKeyStore = new PreKeyStore(getAccountDatabase(), ServiceIdType.ACI));
    }

    private SignedPreKeyStore getAciSignedPreKeyStore() {
        return getOrCreate(() -> aciSignedPreKeyStore,
                () -> aciSignedPreKeyStore = new SignedPreKeyStore(getAccountDatabase(), ServiceIdType.ACI));
    }

    private PreKeyStore getPniPreKeyStore() {
        return getOrCreate(() -> pniPreKeyStore,
                () -> pniPreKeyStore = new PreKeyStore(getAccountDatabase(), ServiceIdType.PNI));
    }

    private SignedPreKeyStore getPniSignedPreKeyStore() {
        return getOrCreate(() -> pniSignedPreKeyStore,
                () -> pniSignedPreKeyStore = new SignedPreKeyStore(getAccountDatabase(), ServiceIdType.PNI));
    }

    public SessionStore getAciSessionStore() {
        return getOrCreate(() -> aciSessionStore,
                () -> aciSessionStore = new SessionStore(getAccountDatabase(), ServiceIdType.ACI));
    }

    public SessionStore getPniSessionStore() {
        return getOrCreate(() -> pniSessionStore,
                () -> pniSessionStore = new SessionStore(getAccountDatabase(), ServiceIdType.PNI));
    }

    public IdentityKeyStore getIdentityKeyStore() {
        return getOrCreate(() -> identityKeyStore,
                () -> identityKeyStore = new IdentityKeyStore(getAccountDatabase(), settings.trustNewIdentity()));
    }

    public SignalIdentityKeyStore getAciIdentityKeyStore() {
        return getOrCreate(() -> aciIdentityKeyStore,
                () -> aciIdentityKeyStore = new SignalIdentityKeyStore(getRecipientResolver(),
                        () -> aciIdentityKeyPair,
                        localRegistrationId,
                        getIdentityKeyStore()));
    }

    public SignalIdentityKeyStore getPniIdentityKeyStore() {
        return getOrCreate(() -> pniIdentityKeyStore,
                () -> pniIdentityKeyStore = new SignalIdentityKeyStore(getRecipientResolver(),
                        () -> pniIdentityKeyPair,
                        localRegistrationId,
                        getIdentityKeyStore()));
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

    public ConfigurationStore getConfigurationStore() {
        return configurationStore;
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
                return aci;
            }

            @Override
            public PNI getPni() {
                return pni;
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
                getLocalRegistrationId(),
                true,
                null,
                registrationLock != null ? registrationLock : getRegistrationLock(),
                getSelfUnidentifiedAccessKey(),
                isUnrestrictedUnidentifiedAccess(),
                getAccountCapabilities(),
                isDiscoverableByPhoneNumber(),
                encryptedDeviceName,
                getLocalPniRegistrationId(),
                null); // TODO recoveryPassword?
    }

    public AccountAttributes.Capabilities getAccountCapabilities() {
        return getCapabilities(isPrimaryDevice());
    }

    public ServiceId getAccountId(ServiceIdType serviceIdType) {
        return serviceIdType.equals(ServiceIdType.ACI) ? aci : pni;
    }

    public ACI getAci() {
        return aci;
    }

    public void setAci(final ACI aci) {
        this.aci = aci;
        save();
    }

    public PNI getPni() {
        return pni;
    }

    public void setPni(final PNI updatedPni) {
        if (this.pni != null && !this.pni.equals(updatedPni)) {
            // Clear data for old PNI
            identityKeyStore.deleteIdentity(this.pni);
            getPniPreKeyStore().removeAllPreKeys();
            getPniSignedPreKeyStore().removeAllSignedPreKeys();
        }

        this.pni = updatedPni;
        save();
    }

    public void setPni(
            final PNI updatedPni,
            final IdentityKeyPair pniIdentityKeyPair,
            final SignedPreKeyRecord pniSignedPreKey,
            final int localPniRegistrationId
    ) {
        setPni(updatedPni);

        setPniIdentityKeyPair(pniIdentityKeyPair);
        addPniSignedPreKey(pniSignedPreKey);
        setLocalPniRegistrationId(localPniRegistrationId);
    }

    public SignalServiceAddress getSelfAddress() {
        return new SignalServiceAddress(aci, number);
    }

    public RecipientAddress getSelfRecipientAddress() {
        return new RecipientAddress(aci, pni, number, username);
    }

    public RecipientId getSelfRecipientId() {
        return getRecipientResolver().resolveRecipient(getSelfRecipientAddress());
    }

    public String getSessionId(final String forNumber) {
        if (!forNumber.equals(sessionNumber)) {
            return null;
        }
        return sessionId;
    }

    public void setSessionId(final String sessionNumber, final String sessionId) {
        this.sessionNumber = sessionNumber;
        this.sessionId = sessionId;
        save();
    }

    public byte[] getEncryptedDeviceName() {
        return encryptedDeviceName == null ? null : Base64.getDecoder().decode(encryptedDeviceName);
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
        return serviceIdType.equals(ServiceIdType.ACI) ? aciIdentityKeyPair : pniIdentityKeyPair;
    }

    public IdentityKeyPair getAciIdentityKeyPair() {
        return aciIdentityKeyPair;
    }

    public IdentityKeyPair getPniIdentityKeyPair() {
        return pniIdentityKeyPair;
    }

    public void setPniIdentityKeyPair(final IdentityKeyPair identityKeyPair) {
        pniIdentityKeyPair = identityKeyPair;
        final var pniPublicKey = identityKeyPair.getPublicKey();
        getIdentityKeyStore().saveIdentity(getPni(), pniPublicKey);
        getIdentityKeyStore().setIdentityTrustLevel(getPni(), pniPublicKey, TrustLevel.TRUSTED_VERIFIED);
        save();
    }

    public int getLocalRegistrationId() {
        return localRegistrationId;
    }

    public int getLocalPniRegistrationId() {
        return localPniRegistrationId;
    }

    public void setLocalPniRegistrationId(final int localPniRegistrationId) {
        this.localPniRegistrationId = localPniRegistrationId;
        save();
    }

    public String getPassword() {
        return password;
    }

    private void setPassword(final String password) {
        this.password = password;
        save();
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
        return this.storageManifestVersion;
    }

    public void setStorageManifestVersion(final long storageManifestVersion) {
        if (storageManifestVersion == this.storageManifestVersion) {
            return;
        }
        this.storageManifestVersion = storageManifestVersion;
        save();
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

    public int getPreKeyIdOffset(ServiceIdType serviceIdType) {
        return serviceIdType.equals(ServiceIdType.ACI) ? aciPreKeyIdOffset : pniPreKeyIdOffset;
    }

    public int getNextSignedPreKeyId(ServiceIdType serviceIdType) {
        return serviceIdType.equals(ServiceIdType.ACI) ? aciNextSignedPreKeyId : pniNextSignedPreKeyId;
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
        return lastReceiveTimestamp;
    }

    public void setLastReceiveTimestamp(final long lastReceiveTimestamp) {
        this.lastReceiveTimestamp = lastReceiveTimestamp;
        save();
    }

    public boolean isUnrestrictedUnidentifiedAccess() {
        final var profile = getProfileStore().getProfile(getSelfRecipientId());
        return profile != null && profile.getUnidentifiedAccessMode() == Profile.UnidentifiedAccessMode.UNRESTRICTED;
    }

    public boolean isDiscoverableByPhoneNumber() {
        final var phoneNumberUnlisted = configurationStore.getPhoneNumberUnlisted();
        return phoneNumberUnlisted == null || !phoneNumberUnlisted;
    }

    public void finishRegistration(final ACI aci, final PNI pni, final MasterKey masterKey, final String pin) {
        this.pinMasterKey = masterKey;
        this.storageManifestVersion = -1;
        this.setStorageManifest(null);
        this.storageKey = null;
        this.encryptedDeviceName = null;
        this.deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;
        this.isMultiDevice = false;
        this.registered = true;
        this.aci = aci;
        this.pni = pni;
        this.registrationLockPin = pin;
        this.lastReceiveTimestamp = 0;
        save();

        clearAllPreKeys();
        getAciSessionStore().archiveAllSessions();
        getPniSessionStore().archiveAllSessions();
        getSenderKeyStore().deleteAll();
        getRecipientTrustedResolver().resolveSelfRecipientTrusted(getSelfRecipientAddress());
        final var aciPublicKey = getAciIdentityKeyPair().getPublicKey();
        getIdentityKeyStore().saveIdentity(getAci(), aciPublicKey);
        getIdentityKeyStore().setIdentityTrustLevel(getAci(), aciPublicKey, TrustLevel.TRUSTED_VERIFIED);
        if (getPniIdentityKeyPair() == null) {
            setPniIdentityKeyPair(KeyUtils.generateIdentityKeyPair());
        } else {
            final var pniPublicKey = getPniIdentityKeyPair().getPublicKey();
            getIdentityKeyStore().saveIdentity(getPni(), pniPublicKey);
            getIdentityKeyStore().setIdentityTrustLevel(getPni(), pniPublicKey, TrustLevel.TRUSTED_VERIFIED);
        }
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
                try {
                    accountDatabase.close();
                } catch (SQLException e) {
                    logger.warn("Failed to close account database: {}", e.getMessage(), e);
                }
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
}
