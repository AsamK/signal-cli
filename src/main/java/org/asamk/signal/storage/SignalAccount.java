package org.asamk.signal.storage;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.asamk.signal.storage.contacts.ContactInfo;
import org.asamk.signal.storage.contacts.JsonContactsStore;
import org.asamk.signal.storage.groups.GroupInfo;
import org.asamk.signal.storage.groups.JsonGroupStore;
import org.asamk.signal.storage.protocol.JsonSignalProtocolStore;
import org.asamk.signal.storage.protocol.SignalServiceAddressResolver;
import org.asamk.signal.storage.threads.LegacyJsonThreadStore;
import org.asamk.signal.storage.threads.ThreadInfo;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.Util;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Collection;
import java.util.UUID;

public class SignalAccount {

    private final ObjectMapper jsonProcessor = new ObjectMapper();
    private FileChannel fileChannel;
    private FileLock lock;
    private String username;
    private UUID uuid;
    private int deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;
    private boolean isMultiDevice = false;
    private String password;
    private String registrationLockPin;
    private String signalingKey;
    private ProfileKey profileKey;
    private int preKeyIdOffset;
    private int nextSignedPreKeyId;

    private boolean registered = false;

    private JsonSignalProtocolStore signalProtocolStore;
    private JsonGroupStore groupStore;
    private JsonContactsStore contactStore;

    private SignalAccount() {
        jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE); // disable autodetect
        jsonProcessor.enable(SerializationFeature.INDENT_OUTPUT); // for pretty print, you can disable it.
        jsonProcessor.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        jsonProcessor.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonProcessor.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    public static SignalAccount load(String dataPath, String username) throws IOException {
        SignalAccount account = new SignalAccount();
        IOUtils.createPrivateDirectories(dataPath);
        account.openFileChannel(getFileName(dataPath, username));
        account.load();
        return account;
    }

    public static SignalAccount create(String dataPath, String username, IdentityKeyPair identityKey, int registrationId, ProfileKey profileKey) throws IOException {
        IOUtils.createPrivateDirectories(dataPath);

        SignalAccount account = new SignalAccount();
        account.openFileChannel(getFileName(dataPath, username));

        account.username = username;
        account.profileKey = profileKey;
        account.signalProtocolStore = new JsonSignalProtocolStore(identityKey, registrationId);
        account.groupStore = new JsonGroupStore();
        account.contactStore = new JsonContactsStore();
        account.registered = false;

        return account;
    }

    public static SignalAccount createLinkedAccount(String dataPath, String username, UUID uuid, String password, int deviceId, IdentityKeyPair identityKey, int registrationId, String signalingKey, ProfileKey profileKey) throws IOException {
        IOUtils.createPrivateDirectories(dataPath);

        SignalAccount account = new SignalAccount();
        account.openFileChannel(getFileName(dataPath, username));

        account.username = username;
        account.uuid = uuid;
        account.password = password;
        account.profileKey = profileKey;
        account.deviceId = deviceId;
        account.signalingKey = signalingKey;
        account.signalProtocolStore = new JsonSignalProtocolStore(identityKey, registrationId);
        account.groupStore = new JsonGroupStore();
        account.contactStore = new JsonContactsStore();
        account.registered = true;
        account.isMultiDevice = true;

        return account;
    }

    public static SignalAccount createTemporaryAccount(IdentityKeyPair identityKey, int registrationId) {
        SignalAccount account = new SignalAccount();

        account.signalProtocolStore = new JsonSignalProtocolStore(identityKey, registrationId);
        account.registered = false;

        return account;
    }

    public static String getFileName(String dataPath, String username) {
        return dataPath + "/" + username;
    }

    public static boolean userExists(String dataPath, String username) {
        if (username == null) {
            return false;
        }
        File f = new File(getFileName(dataPath, username));
        return !(!f.exists() || f.isDirectory());
    }

    private void load() throws IOException {
        JsonNode rootNode;
        synchronized (fileChannel) {
            fileChannel.position(0);
            rootNode = jsonProcessor.readTree(Channels.newInputStream(fileChannel));
        }

        JsonNode uuidNode = rootNode.get("uuid");
        if (uuidNode != null && !uuidNode.isNull()) {
            try {
                uuid = UUID.fromString(uuidNode.asText());
            } catch (IllegalArgumentException e) {
                throw new IOException("Config file contains an invalid uuid, needs to be a valid UUID", e);
            }
        }
        JsonNode node = rootNode.get("deviceId");
        if (node != null) {
            deviceId = node.asInt();
        }
        if (rootNode.has("isMultiDevice")) {
            isMultiDevice = Util.getNotNullNode(rootNode, "isMultiDevice").asBoolean();
        }
        username = Util.getNotNullNode(rootNode, "username").asText();
        password = Util.getNotNullNode(rootNode, "password").asText();
        JsonNode pinNode = rootNode.get("registrationLockPin");
        registrationLockPin = pinNode == null || pinNode.isNull() ? null : pinNode.asText();
        if (rootNode.has("signalingKey")) {
            signalingKey = Util.getNotNullNode(rootNode, "signalingKey").asText();
        }
        if (rootNode.has("preKeyIdOffset")) {
            preKeyIdOffset = Util.getNotNullNode(rootNode, "preKeyIdOffset").asInt(0);
        } else {
            preKeyIdOffset = 0;
        }
        if (rootNode.has("nextSignedPreKeyId")) {
            nextSignedPreKeyId = Util.getNotNullNode(rootNode, "nextSignedPreKeyId").asInt();
        } else {
            nextSignedPreKeyId = 0;
        }
        if (rootNode.has("profileKey")) {
            try {
                profileKey = new ProfileKey(Base64.decode(Util.getNotNullNode(rootNode, "profileKey").asText()));
            } catch (InvalidInputException e) {
                throw new IOException("Config file contains an invalid profileKey, needs to be base64 encoded array of 32 bytes", e);
            }
        }

        signalProtocolStore = jsonProcessor.convertValue(Util.getNotNullNode(rootNode, "axolotlStore"), JsonSignalProtocolStore.class);
        registered = Util.getNotNullNode(rootNode, "registered").asBoolean();
        JsonNode groupStoreNode = rootNode.get("groupStore");
        if (groupStoreNode != null) {
            groupStore = jsonProcessor.convertValue(groupStoreNode, JsonGroupStore.class);
        }
        if (groupStore == null) {
            groupStore = new JsonGroupStore();
        }

        JsonNode contactStoreNode = rootNode.get("contactStore");
        if (contactStoreNode != null) {
            contactStore = jsonProcessor.convertValue(contactStoreNode, JsonContactsStore.class);
        }
        if (contactStore == null) {
            contactStore = new JsonContactsStore();
        }
        JsonNode threadStoreNode = rootNode.get("threadStore");
        if (threadStoreNode != null) {
            LegacyJsonThreadStore threadStore = jsonProcessor.convertValue(threadStoreNode, LegacyJsonThreadStore.class);
            // Migrate thread info to group and contact store
            for (ThreadInfo thread : threadStore.getThreads()) {
                if (thread.id == null || thread.id.isEmpty()) {
                    continue;
                }
                try {
                    ContactInfo contactInfo = contactStore.getContact(new SignalServiceAddress(null, thread.id));
                    if (contactInfo != null) {
                        contactInfo.messageExpirationTime = thread.messageExpirationTime;
                        contactStore.updateContact(contactInfo);
                    } else {
                        GroupInfo groupInfo = groupStore.getGroup(Base64.decode(thread.id));
                        if (groupInfo != null) {
                            groupInfo.messageExpirationTime = thread.messageExpirationTime;
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
        ObjectNode rootNode = jsonProcessor.createObjectNode();
        rootNode.put("username", username)
                .put("uuid", uuid == null ? null : uuid.toString())
                .put("deviceId", deviceId)
                .put("isMultiDevice", isMultiDevice)
                .put("password", password)
                .put("registrationLockPin", registrationLockPin)
                .put("signalingKey", signalingKey)
                .put("preKeyIdOffset", preKeyIdOffset)
                .put("nextSignedPreKeyId", nextSignedPreKeyId)
                .put("profileKey", Base64.encodeBytes(profileKey.serialize()))
                .put("registered", registered)
                .putPOJO("axolotlStore", signalProtocolStore)
                .putPOJO("groupStore", groupStore)
                .putPOJO("contactStore", contactStore)
        ;
        try {
            synchronized (fileChannel) {
                fileChannel.position(0);
                jsonProcessor.writeValue(Channels.newOutputStream(fileChannel), rootNode);
                fileChannel.truncate(fileChannel.position());
                fileChannel.force(false);
            }
        } catch (Exception e) {
            System.err.println(String.format("Error saving file: %s", e.getMessage()));
        }
    }

    private void openFileChannel(String fileName) throws IOException {
        if (fileChannel != null) {
            return;
        }

        if (!new File(fileName).exists()) {
            IOUtils.createPrivateFile(fileName);
        }
        fileChannel = new RandomAccessFile(new File(fileName), "rw").getChannel();
        lock = fileChannel.tryLock();
        if (lock == null) {
            System.err.println("Config file is in use by another instance, waiting…");
            lock = fileChannel.lock();
            System.err.println("Config file lock acquired.");
        }
    }

    public void setResolver(final SignalServiceAddressResolver resolver) {
        signalProtocolStore.setResolver(resolver);
    }

    public void addPreKeys(Collection<PreKeyRecord> records) {
        for (PreKeyRecord record : records) {
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

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String getRegistrationLockPin() {
        return registrationLockPin;
    }

    public String getRegistrationLock() {
        return null; // TODO implement KBS
    }

    public void setRegistrationLockPin(final String registrationLockPin) {
        this.registrationLockPin = registrationLockPin;
    }

    public String getSignalingKey() {
        return signalingKey;
    }

    public void setSignalingKey(final String signalingKey) {
        this.signalingKey = signalingKey;
    }

    public ProfileKey getProfileKey() {
        return profileKey;
    }

    public void setProfileKey(final ProfileKey profileKey) {
        this.profileKey = profileKey;
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
}
