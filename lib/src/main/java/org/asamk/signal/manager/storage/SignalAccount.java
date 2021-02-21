package org.asamk.signal.manager.storage;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.storage.contacts.JsonContactsStore;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.JsonGroupStore;
import org.asamk.signal.manager.storage.messageCache.MessageCache;
import org.asamk.signal.manager.storage.profiles.ProfileStore;
import org.asamk.signal.manager.storage.protocol.JsonSignalProtocolStore;
import org.asamk.signal.manager.storage.protocol.RecipientStore;
import org.asamk.signal.manager.storage.protocol.SignalServiceAddressResolver;
import org.asamk.signal.manager.storage.stickers.StickerStore;
import org.asamk.signal.manager.storage.threads.LegacyJsonThreadStore;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.KeyUtils;
import org.asamk.signal.manager.util.Utils;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.StorageKey;

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
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

public class SignalAccount implements Closeable {

    private final static Logger logger = LoggerFactory.getLogger(SignalAccount.class);

    private final ObjectMapper jsonProcessor = new ObjectMapper();
    private final FileChannel fileChannel;
    private final FileLock lock;
    private String username;
    private UUID uuid;
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

    private JsonSignalProtocolStore signalProtocolStore;
    private JsonGroupStore groupStore;
    private JsonContactsStore contactStore;
    private RecipientStore recipientStore;
    private ProfileStore profileStore;
    private StickerStore stickerStore;

    private MessageCache messageCache;

    private SignalAccount(final FileChannel fileChannel, final FileLock lock) {
        this.fileChannel = fileChannel;
        this.lock = lock;
        jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE); // disable autodetect
        jsonProcessor.enable(SerializationFeature.INDENT_OUTPUT); // for pretty print
        jsonProcessor.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonProcessor.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    public static SignalAccount load(File dataPath, String username) throws IOException {
        final var fileName = getFileName(dataPath, username);
        final var pair = openFileChannel(fileName);
        try {
            var account = new SignalAccount(pair.first(), pair.second());
            account.load(dataPath);
            account.migrateLegacyConfigs();

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

        final var pair = openFileChannel(fileName);
        var account = new SignalAccount(pair.first(), pair.second());

        account.username = username;
        account.profileKey = profileKey;
        account.signalProtocolStore = new JsonSignalProtocolStore(identityKey, registrationId);
        account.groupStore = new JsonGroupStore(getGroupCachePath(dataPath, username));
        account.contactStore = new JsonContactsStore();
        account.recipientStore = new RecipientStore();
        account.profileStore = new ProfileStore();
        account.stickerStore = new StickerStore();

        account.messageCache = new MessageCache(getMessageCachePath(dataPath, username));

        account.registered = false;

        account.migrateLegacyConfigs();

        return account;
    }

    public static SignalAccount createLinkedAccount(
            File dataPath,
            String username,
            UUID uuid,
            String password,
            int deviceId,
            IdentityKeyPair identityKey,
            int registrationId,
            ProfileKey profileKey
    ) throws IOException {
        IOUtils.createPrivateDirectories(dataPath);
        var fileName = getFileName(dataPath, username);
        if (!fileName.exists()) {
            IOUtils.createPrivateFile(fileName);
        }

        final var pair = openFileChannel(fileName);
        var account = new SignalAccount(pair.first(), pair.second());

        account.username = username;
        account.uuid = uuid;
        account.password = password;
        account.profileKey = profileKey;
        account.deviceId = deviceId;
        account.signalProtocolStore = new JsonSignalProtocolStore(identityKey, registrationId);
        account.groupStore = new JsonGroupStore(getGroupCachePath(dataPath, username));
        account.contactStore = new JsonContactsStore();
        account.recipientStore = new RecipientStore();
        account.profileStore = new ProfileStore();
        account.stickerStore = new StickerStore();

        account.messageCache = new MessageCache(getMessageCachePath(dataPath, username));

        account.registered = true;
        account.isMultiDevice = true;

        account.migrateLegacyConfigs();

        return account;
    }

    public void migrateLegacyConfigs() {
        if (getProfileKey() == null && isRegistered()) {
            // Old config file, creating new profile key
            setProfileKey(KeyUtils.createProfileKey());
            save();
        }
        // Store profile keys only in profile store
        for (var contact : getContactStore().getContacts()) {
            var profileKeyString = contact.profileKey;
            if (profileKeyString == null) {
                continue;
            }
            final ProfileKey profileKey;
            try {
                profileKey = new ProfileKey(Base64.getDecoder().decode(profileKeyString));
            } catch (InvalidInputException ignored) {
                continue;
            }
            contact.profileKey = null;
            getProfileStore().storeProfileKey(contact.getAddress(), profileKey);
        }
        // Ensure our profile key is stored in profile store
        getProfileStore().storeProfileKey(getSelfAddress(), getProfileKey());
    }

    public static File getFileName(File dataPath, String username) {
        return new File(dataPath, username);
    }

    private static File getUserPath(final File dataPath, final String username) {
        return new File(dataPath, username + ".d");
    }

    public static File getMessageCachePath(File dataPath, String username) {
        return new File(getUserPath(dataPath, username), "msg-cache");
    }

    private static File getGroupCachePath(File dataPath, String username) {
        return new File(getUserPath(dataPath, username), "group-cache");
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

        if (rootNode.hasNonNull("uuid")) {
            try {
                uuid = UUID.fromString(rootNode.get("uuid").asText());
            } catch (IllegalArgumentException e) {
                throw new IOException("Config file contains an invalid uuid, needs to be a valid UUID", e);
            }
        }
        if (rootNode.hasNonNull("deviceId")) {
            deviceId = rootNode.get("deviceId").asInt();
        }
        if (rootNode.hasNonNull("isMultiDevice")) {
            isMultiDevice = rootNode.get("isMultiDevice").asBoolean();
        }
        username = Utils.getNotNullNode(rootNode, "username").asText();
        password = Utils.getNotNullNode(rootNode, "password").asText();
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

        signalProtocolStore = jsonProcessor.convertValue(Utils.getNotNullNode(rootNode, "axolotlStore"),
                JsonSignalProtocolStore.class);
        registered = Utils.getNotNullNode(rootNode, "registered").asBoolean();
        var groupStoreNode = rootNode.get("groupStore");
        if (groupStoreNode != null) {
            groupStore = jsonProcessor.convertValue(groupStoreNode, JsonGroupStore.class);
            groupStore.groupCachePath = getGroupCachePath(dataPath, username);
        }
        if (groupStore == null) {
            groupStore = new JsonGroupStore(getGroupCachePath(dataPath, username));
        }

        var contactStoreNode = rootNode.get("contactStore");
        if (contactStoreNode != null) {
            contactStore = jsonProcessor.convertValue(contactStoreNode, JsonContactsStore.class);
        }
        if (contactStore == null) {
            contactStore = new JsonContactsStore();
        }

        var recipientStoreNode = rootNode.get("recipientStore");
        if (recipientStoreNode != null) {
            recipientStore = jsonProcessor.convertValue(recipientStoreNode, RecipientStore.class);
        }
        if (recipientStore == null) {
            recipientStore = new RecipientStore();

            recipientStore.resolveServiceAddress(getSelfAddress());

            for (var contact : contactStore.getContacts()) {
                recipientStore.resolveServiceAddress(contact.getAddress());
            }

            for (var group : groupStore.getGroups()) {
                if (group instanceof GroupInfoV1) {
                    var groupInfoV1 = (GroupInfoV1) group;
                    groupInfoV1.members = groupInfoV1.members.stream()
                            .map(m -> recipientStore.resolveServiceAddress(m))
                            .collect(Collectors.toSet());
                }
            }

            for (var session : signalProtocolStore.getSessions()) {
                session.address = recipientStore.resolveServiceAddress(session.address);
            }

            for (var identity : signalProtocolStore.getIdentities()) {
                identity.setAddress(recipientStore.resolveServiceAddress(identity.getAddress()));
            }
        }

        var profileStoreNode = rootNode.get("profileStore");
        if (profileStoreNode != null) {
            profileStore = jsonProcessor.convertValue(profileStoreNode, ProfileStore.class);
        }
        if (profileStore == null) {
            profileStore = new ProfileStore();
        }

        var stickerStoreNode = rootNode.get("stickerStore");
        if (stickerStoreNode != null) {
            stickerStore = jsonProcessor.convertValue(stickerStoreNode, StickerStore.class);
        }
        if (stickerStore == null) {
            stickerStore = new StickerStore();
        }

        messageCache = new MessageCache(getMessageCachePath(dataPath, username));

        var threadStoreNode = rootNode.get("threadStore");
        if (threadStoreNode != null && !threadStoreNode.isNull()) {
            var threadStore = jsonProcessor.convertValue(threadStoreNode, LegacyJsonThreadStore.class);
            // Migrate thread info to group and contact store
            for (var thread : threadStore.getThreads()) {
                if (thread.id == null || thread.id.isEmpty()) {
                    continue;
                }
                try {
                    var contactInfo = contactStore.getContact(new SignalServiceAddress(null, thread.id));
                    if (contactInfo != null) {
                        contactInfo.messageExpirationTime = thread.messageExpirationTime;
                        contactStore.updateContact(contactInfo);
                    } else {
                        var groupInfo = groupStore.getGroup(GroupId.fromBase64(thread.id));
                        if (groupInfo instanceof GroupInfoV1) {
                            ((GroupInfoV1) groupInfo).messageExpirationTime = thread.messageExpirationTime;
                            groupStore.updateGroup(groupInfo);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void save() {
        if (fileChannel == null) {
            return;
        }
        var rootNode = jsonProcessor.createObjectNode();
        rootNode.put("username", username)
                .put("uuid", uuid == null ? null : uuid.toString())
                .put("deviceId", deviceId)
                .put("isMultiDevice", isMultiDevice)
                .put("password", password)
                .put("registrationLockPin", registrationLockPin)
                .put("pinMasterKey",
                        pinMasterKey == null ? null : Base64.getEncoder().encodeToString(pinMasterKey.serialize()))
                .put("storageKey",
                        storageKey == null ? null : Base64.getEncoder().encodeToString(storageKey.serialize()))
                .put("preKeyIdOffset", preKeyIdOffset)
                .put("nextSignedPreKeyId", nextSignedPreKeyId)
                .put("profileKey", Base64.getEncoder().encodeToString(profileKey.serialize()))
                .put("registered", registered)
                .putPOJO("axolotlStore", signalProtocolStore)
                .putPOJO("groupStore", groupStore)
                .putPOJO("contactStore", contactStore)
                .putPOJO("recipientStore", recipientStore)
                .putPOJO("profileStore", profileStore)
                .putPOJO("stickerStore", stickerStore);
        try {
            try (var output = new ByteArrayOutputStream()) {
                // Write to memory first to prevent corrupting the file in case of serialization errors
                jsonProcessor.writeValue(output, rootNode);
                var input = new ByteArrayInputStream(output.toByteArray());
                synchronized (fileChannel) {
                    fileChannel.position(0);
                    input.transferTo(Channels.newOutputStream(fileChannel));
                    fileChannel.truncate(fileChannel.position());
                    fileChannel.force(false);
                }
            }
        } catch (Exception e) {
            logger.error("Error saving file: {}", e.getMessage());
        }
    }

    private static Pair<FileChannel, FileLock> openFileChannel(File fileName) throws IOException {
        var fileChannel = new RandomAccessFile(fileName, "rw").getChannel();
        var lock = fileChannel.tryLock();
        if (lock == null) {
            logger.info("Config file is in use by another instance, waiting…");
            lock = fileChannel.lock();
            logger.info("Config file lock acquired.");
        }
        return new Pair<>(fileChannel, lock);
    }

    public void setResolver(final SignalServiceAddressResolver resolver) {
        signalProtocolStore.setResolver(resolver);
    }

    public void addPreKeys(Collection<PreKeyRecord> records) {
        for (var record : records) {
            signalProtocolStore.storePreKey(record.getId(), record);
        }
        preKeyIdOffset = (preKeyIdOffset + records.size()) % Medium.MAX_VALUE;
    }

    public void addSignedPreKey(SignedPreKeyRecord record) {
        signalProtocolStore.storeSignedPreKey(record.getId(), record);
        nextSignedPreKeyId = (nextSignedPreKeyId + 1) % Medium.MAX_VALUE;
    }

    public JsonSignalProtocolStore getSignalProtocolStore() {
        return signalProtocolStore;
    }

    public JsonGroupStore getGroupStore() {
        return groupStore;
    }

    public JsonContactsStore getContactStore() {
        return contactStore;
    }

    public RecipientStore getRecipientStore() {
        return recipientStore;
    }

    public ProfileStore getProfileStore() {
        return profileStore;
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
    }

    public SignalServiceAddress getSelfAddress() {
        return new SignalServiceAddress(uuid, username);
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(final int deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isMasterDevice() {
        return deviceId == SignalServiceAddress.DEFAULT_DEVICE_ID;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String getRegistrationLockPin() {
        return registrationLockPin;
    }

    public void setRegistrationLockPin(final String registrationLockPin) {
        this.registrationLockPin = registrationLockPin;
    }

    public MasterKey getPinMasterKey() {
        return pinMasterKey;
    }

    public void setPinMasterKey(final MasterKey pinMasterKey) {
        this.pinMasterKey = pinMasterKey;
    }

    public StorageKey getStorageKey() {
        if (pinMasterKey != null) {
            return pinMasterKey.deriveStorageServiceKey();
        }
        return storageKey;
    }

    public void setStorageKey(final StorageKey storageKey) {
        this.storageKey = storageKey;
    }

    public ProfileKey getProfileKey() {
        return profileKey;
    }

    public void setProfileKey(final ProfileKey profileKey) {
        this.profileKey = profileKey;
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
    }

    public boolean isMultiDevice() {
        return isMultiDevice;
    }

    public void setMultiDevice(final boolean multiDevice) {
        isMultiDevice = multiDevice;
    }

    public boolean isUnrestrictedUnidentifiedAccess() {
        // TODO make configurable
        return false;
    }

    public boolean isDiscoverableByPhoneNumber() {
        // TODO make configurable
        return true;
    }

    @Override
    public void close() throws IOException {
        if (fileChannel.isOpen()) {
            save();
        }
        synchronized (fileChannel) {
            try {
                lock.close();
            } catch (ClosedChannelException ignored) {
            }
            fileChannel.close();
        }
    }
}
