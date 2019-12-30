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

import org.asamk.signal.storage.contacts.JsonContactsStore;
import org.asamk.signal.storage.groups.JsonGroupStore;
import org.asamk.signal.storage.protocol.JsonSignalProtocolStore;
import org.asamk.signal.storage.threads.JsonThreadStore;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.Util;
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

public class SignalAccount {

    private final ObjectMapper jsonProcessor = new ObjectMapper();
    private FileChannel fileChannel;
    private FileLock lock;
    private String username;
    private int deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;
    private boolean isMultiDevice = false;
    private String password;
    private String registrationLockPin;
    private String signalingKey;
    private byte[] profileKey;
    private int preKeyIdOffset;
    private int nextSignedPreKeyId;

    private boolean registered = false;

    private JsonSignalProtocolStore signalProtocolStore;
    private JsonGroupStore groupStore;
    private JsonContactsStore contactStore;
    private JsonThreadStore threadStore;

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

    public static SignalAccount create(String dataPath, String username, IdentityKeyPair identityKey, int registrationId, byte[] profileKey) throws IOException {
        IOUtils.createPrivateDirectories(dataPath);

        SignalAccount account = new SignalAccount();
        account.openFileChannel(getFileName(dataPath, username));

        account.username = username;
        account.profileKey = profileKey;
        account.signalProtocolStore = new JsonSignalProtocolStore(identityKey, registrationId);
        account.groupStore = new JsonGroupStore();
        account.threadStore = new JsonThreadStore();
        account.contactStore = new JsonContactsStore();
        account.registered = false;

        return account;
    }

    public static SignalAccount createLinkedAccount(String dataPath, String username, String password, int deviceId, IdentityKeyPair identityKey, int registrationId, String signalingKey, byte[] profileKey) throws IOException {
        IOUtils.createPrivateDirectories(dataPath);

        SignalAccount account = new SignalAccount();
        account.openFileChannel(getFileName(dataPath, username));

        account.username = username;
        account.password = password;
        account.profileKey = profileKey;
        account.deviceId = deviceId;
        account.signalingKey = signalingKey;
        account.signalProtocolStore = new JsonSignalProtocolStore(identityKey, registrationId);
        account.groupStore = new JsonGroupStore();
        account.threadStore = new JsonThreadStore();
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
            profileKey = Base64.decode(Util.getNotNullNode(rootNode, "profileKey").asText());
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
            threadStore = jsonProcessor.convertValue(threadStoreNode, JsonThreadStore.class);
        }
        if (threadStore == null) {
            threadStore = new JsonThreadStore();
        }
    }

    public void save() {
        if (fileChannel == null) {
            return;
        }
        ObjectNode rootNode = jsonProcessor.createObjectNode();
        rootNode.put("username", username)
                .put("deviceId", deviceId)
                .put("isMultiDevice", isMultiDevice)
                .put("password", password)
                .put("registrationLockPin", registrationLockPin)
                .put("signalingKey", signalingKey)
                .put("preKeyIdOffset", preKeyIdOffset)
                .put("nextSignedPreKeyId", nextSignedPreKeyId)
                .put("profileKey", Base64.encodeBytes(profileKey))
                .put("registered", registered)
                .putPOJO("axolotlStore", signalProtocolStore)
                .putPOJO("groupStore", groupStore)
                .putPOJO("contactStore", contactStore)
                .putPOJO("threadStore", threadStore)
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

    public JsonThreadStore getThreadStore() {
        return threadStore;
    }

    public String getUsername() {
        return username;
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

    public void setRegistrationLockPin(final String registrationLockPin) {
        this.registrationLockPin = registrationLockPin;
    }

    public String getSignalingKey() {
        return signalingKey;
    }

    public void setSignalingKey(final String signalingKey) {
        this.signalingKey = signalingKey;
    }

    public byte[] getProfileKey() {
        return profileKey;
    }

    public void setProfileKey(final byte[] profileKey) {
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
