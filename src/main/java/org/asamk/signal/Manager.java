/**
 * Copyright (C) 2015 AsamK
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.asamk.signal;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.util.TextUtils;
import org.asamk.Signal;
import org.asamk.signal.storage.contacts.ContactInfo;
import org.asamk.signal.storage.contacts.JsonContactsStore;
import org.asamk.signal.storage.groups.GroupInfo;
import org.asamk.signal.storage.groups.JsonGroupStore;
import org.asamk.signal.storage.protocol.JsonIdentityKeyStore;
import org.asamk.signal.storage.protocol.JsonSignalProtocolStore;
import org.asamk.signal.storage.threads.JsonThreadStore;
import org.asamk.signal.storage.threads.ThreadInfo;
import org.asamk.signal.util.Util;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.messages.multidevice.*;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.push.exceptions.*;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceUrl;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.file.attribute.PosixFilePermission.*;

class Manager implements Signal {
    private final static String URL = "https://textsecure-service.whispersystems.org";
    private final static TrustStore TRUST_STORE = new WhisperTrustStore();
    private final static SignalServiceUrl[] serviceUrls = new SignalServiceUrl[]{new SignalServiceUrl(URL, TRUST_STORE)};

    public final static String PROJECT_NAME = Manager.class.getPackage().getImplementationTitle();
    public final static String PROJECT_VERSION = Manager.class.getPackage().getImplementationVersion();
    private final static String USER_AGENT = PROJECT_NAME == null ? null : PROJECT_NAME + " " + PROJECT_VERSION;

    private final static int PREKEY_MINIMUM_COUNT = 20;
    private static final int PREKEY_BATCH_SIZE = 100;
    private static final int MAX_ATTACHMENT_SIZE = 150 * 1024 * 1024;

    private final String settingsPath;
    private final String dataPath;
    private final String attachmentsPath;
    private final String avatarsPath;

    private FileChannel fileChannel;
    private FileLock lock;

    private final ObjectMapper jsonProcessor = new ObjectMapper();
    private String username;
    private int deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;
    private String password;
    private String signalingKey;
    private int preKeyIdOffset;
    private int nextSignedPreKeyId;

    private boolean registered = false;

    private JsonSignalProtocolStore signalProtocolStore;
    private SignalServiceAccountManager accountManager;
    private JsonGroupStore groupStore;
    private JsonContactsStore contactStore;
    private JsonThreadStore threadStore;
    private SignalServiceMessagePipe messagePipe = null;

    public Manager(String username, String settingsPath) {
        this.username = username;
        this.settingsPath = settingsPath;
        this.dataPath = this.settingsPath + "/data";
        this.attachmentsPath = this.settingsPath + "/attachments";
        this.avatarsPath = this.settingsPath + "/avatars";

        jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE); // disable autodetect
        jsonProcessor.enable(SerializationFeature.INDENT_OUTPUT); // for pretty print, you can disable it.
        jsonProcessor.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        jsonProcessor.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonProcessor.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    public String getUsername() {
        return username;
    }

    private IdentityKey getIdentity() {
        return signalProtocolStore.getIdentityKeyPair().getPublicKey();
    }

    public int getDeviceId() {
        return deviceId;
    }

    public String getFileName() {
        return dataPath + "/" + username;
    }

    private String getMessageCachePath() {
        return this.dataPath + "/" + username + ".d/msg-cache";
    }

    private String getMessageCachePath(String sender) {
        return getMessageCachePath() + "/" + sender.replace("/", "_");
    }

    private File getMessageCacheFile(String sender, long now, long timestamp) throws IOException {
        String cachePath = getMessageCachePath(sender);
        createPrivateDirectories(cachePath);
        return new File(cachePath + "/" + now + "_" + timestamp);
    }

    private static void createPrivateDirectories(String path) throws IOException {
        final Path file = new File(path).toPath();
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE);
            Files.createDirectories(file, PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(file);
        }
    }

    private static void createPrivateFile(String path) throws IOException {
        final Path file = new File(path).toPath();
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE);
            Files.createFile(file, PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createFile(file);
        }
    }

    public boolean userExists() {
        if (username == null) {
            return false;
        }
        File f = new File(getFileName());
        return !(!f.exists() || f.isDirectory());
    }

    public boolean userHasKeys() {
        return signalProtocolStore != null;
    }

    private JsonNode getNotNullNode(JsonNode parent, String name) throws InvalidObjectException {
        JsonNode node = parent.get(name);
        if (node == null) {
            throw new InvalidObjectException(String.format("Incorrect file format: expected parameter %s not found ", name));
        }

        return node;
    }

    private void openFileChannel() throws IOException {
        if (fileChannel != null)
            return;

        createPrivateDirectories(dataPath);
        if (!new File(getFileName()).exists()) {
            createPrivateFile(getFileName());
        }
        fileChannel = new RandomAccessFile(new File(getFileName()), "rw").getChannel();
        lock = fileChannel.tryLock();
        if (lock == null) {
            System.err.println("Config file is in use by another instance, waiting…");
            lock = fileChannel.lock();
            System.err.println("Config file lock acquired.");
        }
    }

    public void init() throws IOException {
        load();

        migrateLegacyConfigs();

        accountManager = new SignalServiceAccountManager(serviceUrls, username, password, deviceId, USER_AGENT);
        try {
            if (registered && accountManager.getPreKeysCount() < PREKEY_MINIMUM_COUNT) {
                refreshPreKeys();
                save();
            }
        } catch (AuthorizationFailedException e) {
            System.err.println("Authorization failed, was the number registered elsewhere?");
        }
    }

    private void load() throws IOException {
        openFileChannel();
        JsonNode rootNode = jsonProcessor.readTree(Channels.newInputStream(fileChannel));

        JsonNode node = rootNode.get("deviceId");
        if (node != null) {
            deviceId = node.asInt();
        }
        username = getNotNullNode(rootNode, "username").asText();
        password = getNotNullNode(rootNode, "password").asText();
        if (rootNode.has("signalingKey")) {
            signalingKey = getNotNullNode(rootNode, "signalingKey").asText();
        }
        if (rootNode.has("preKeyIdOffset")) {
            preKeyIdOffset = getNotNullNode(rootNode, "preKeyIdOffset").asInt(0);
        } else {
            preKeyIdOffset = 0;
        }
        if (rootNode.has("nextSignedPreKeyId")) {
            nextSignedPreKeyId = getNotNullNode(rootNode, "nextSignedPreKeyId").asInt();
        } else {
            nextSignedPreKeyId = 0;
        }
        signalProtocolStore = jsonProcessor.convertValue(getNotNullNode(rootNode, "axolotlStore"), JsonSignalProtocolStore.class);
        registered = getNotNullNode(rootNode, "registered").asBoolean();
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

    private void migrateLegacyConfigs() {
        // Copy group avatars that were previously stored in the attachments folder
        // to the new avatar folder
        if (JsonGroupStore.groupsWithLegacyAvatarId.size() > 0) {
            for (GroupInfo g : JsonGroupStore.groupsWithLegacyAvatarId) {
                File avatarFile = getGroupAvatarFile(g.groupId);
                File attachmentFile = getAttachmentFile(g.getAvatarId());
                if (!avatarFile.exists() && attachmentFile.exists()) {
                    try {
                        createPrivateDirectories(avatarsPath);
                        Files.copy(attachmentFile.toPath(), avatarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            JsonGroupStore.groupsWithLegacyAvatarId.clear();
            save();
        }
    }

    private void save() {
        if (username == null) {
            return;
        }
        ObjectNode rootNode = jsonProcessor.createObjectNode();
        rootNode.put("username", username)
                .put("deviceId", deviceId)
                .put("password", password)
                .put("signalingKey", signalingKey)
                .put("preKeyIdOffset", preKeyIdOffset)
                .put("nextSignedPreKeyId", nextSignedPreKeyId)
                .put("registered", registered)
                .putPOJO("axolotlStore", signalProtocolStore)
                .putPOJO("groupStore", groupStore)
                .putPOJO("contactStore", contactStore)
                .putPOJO("threadStore", threadStore)
        ;
        try {
            openFileChannel();
            fileChannel.position(0);
            jsonProcessor.writeValue(Channels.newOutputStream(fileChannel), rootNode);
            fileChannel.truncate(fileChannel.position());
            fileChannel.force(false);
        } catch (Exception e) {
            System.err.println(String.format("Error saving file: %s", e.getMessage()));
        }
    }

    public void createNewIdentity() {
        IdentityKeyPair identityKey = KeyHelper.generateIdentityKeyPair();
        int registrationId = KeyHelper.generateRegistrationId(false);
        signalProtocolStore = new JsonSignalProtocolStore(identityKey, registrationId);
        groupStore = new JsonGroupStore();
        registered = false;
        save();
    }

    public boolean isRegistered() {
        return registered;
    }

    public void register(boolean voiceVerification) throws IOException {
        password = Util.getSecret(18);

        accountManager = new SignalServiceAccountManager(serviceUrls, username, password, USER_AGENT);

        if (voiceVerification)
            accountManager.requestVoiceVerificationCode();
        else
            accountManager.requestSmsVerificationCode();

        registered = false;
        save();
    }

    public void updateAccountAttributes() throws IOException {
        accountManager.setAccountAttributes(signalingKey, signalProtocolStore.getLocalRegistrationId(), false, false, true);
    }

    public void unregister() throws IOException {
        // When setting an empty GCM id, the Signal-Server also sets the fetchesMessages property to false.
        // If this is the master device, other users can't send messages to this number anymore.
        // If this is a linked device, other users can still send messages, but this device doesn't receive them anymore.
        accountManager.setGcmId(Optional.<String>absent());
    }

    public URI getDeviceLinkUri() throws TimeoutException, IOException {
        password = Util.getSecret(18);

        accountManager = new SignalServiceAccountManager(serviceUrls, username, password, USER_AGENT);
        String uuid = accountManager.getNewDeviceUuid();

        registered = false;
        try {
            return new URI("tsdevice:/?uuid=" + URLEncoder.encode(uuid, "utf-8") + "&pub_key=" + URLEncoder.encode(Base64.encodeBytesWithoutPadding(signalProtocolStore.getIdentityKeyPair().getPublicKey().serialize()), "utf-8"));
        } catch (URISyntaxException e) {
            // Shouldn't happen
            return null;
        }
    }

    public void finishDeviceLink(String deviceName) throws IOException, InvalidKeyException, TimeoutException, UserAlreadyExists {
        signalingKey = Util.getSecret(52);
        SignalServiceAccountManager.NewDeviceRegistrationReturn ret = accountManager.finishNewDeviceRegistration(signalProtocolStore.getIdentityKeyPair(), signalingKey, false, true, signalProtocolStore.getLocalRegistrationId(), deviceName);
        deviceId = ret.getDeviceId();
        username = ret.getNumber();
        // TODO do this check before actually registering
        if (userExists()) {
            throw new UserAlreadyExists(username, getFileName());
        }
        signalProtocolStore = new JsonSignalProtocolStore(ret.getIdentity(), signalProtocolStore.getLocalRegistrationId());

        registered = true;
        refreshPreKeys();

        requestSyncGroups();
        requestSyncContacts();

        save();
    }

    public List<DeviceInfo> getLinkedDevices() throws IOException {
        return accountManager.getDevices();
    }

    public void removeLinkedDevices(int deviceId) throws IOException {
        accountManager.removeDevice(deviceId);
    }

    public static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            String name = null;
            try {
                name = URLDecoder.decode(param.split("=")[0], "utf-8");
            } catch (UnsupportedEncodingException e) {
                // Impossible
            }
            String value = null;
            try {
                value = URLDecoder.decode(param.split("=")[1], "utf-8");
            } catch (UnsupportedEncodingException e) {
                // Impossible
            }
            map.put(name, value);
        }
        return map;
    }

    public void addDeviceLink(URI linkUri) throws IOException, InvalidKeyException {
        Map<String, String> query = getQueryMap(linkUri.getRawQuery());
        String deviceIdentifier = query.get("uuid");
        String publicKeyEncoded = query.get("pub_key");

        if (TextUtils.isEmpty(deviceIdentifier) || TextUtils.isEmpty(publicKeyEncoded)) {
            throw new RuntimeException("Invalid device link uri");
        }

        ECPublicKey deviceKey = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);

        addDevice(deviceIdentifier, deviceKey);
    }

    private void addDevice(String deviceIdentifier, ECPublicKey deviceKey) throws IOException, InvalidKeyException {
        IdentityKeyPair identityKeyPair = signalProtocolStore.getIdentityKeyPair();
        String verificationCode = accountManager.getNewDeviceVerificationCode();

        accountManager.addDevice(deviceIdentifier, deviceKey, identityKeyPair, verificationCode);
    }

    private List<PreKeyRecord> generatePreKeys() {
        List<PreKeyRecord> records = new LinkedList<>();

        for (int i = 0; i < PREKEY_BATCH_SIZE; i++) {
            int preKeyId = (preKeyIdOffset + i) % Medium.MAX_VALUE;
            ECKeyPair keyPair = Curve.generateKeyPair();
            PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

            signalProtocolStore.storePreKey(preKeyId, record);
            records.add(record);
        }

        preKeyIdOffset = (preKeyIdOffset + PREKEY_BATCH_SIZE + 1) % Medium.MAX_VALUE;
        save();

        return records;
    }

    private PreKeyRecord getOrGenerateLastResortPreKey() {
        if (signalProtocolStore.containsPreKey(Medium.MAX_VALUE)) {
            try {
                return signalProtocolStore.loadPreKey(Medium.MAX_VALUE);
            } catch (InvalidKeyIdException e) {
                signalProtocolStore.removePreKey(Medium.MAX_VALUE);
            }
        }

        ECKeyPair keyPair = Curve.generateKeyPair();
        PreKeyRecord record = new PreKeyRecord(Medium.MAX_VALUE, keyPair);

        signalProtocolStore.storePreKey(Medium.MAX_VALUE, record);
        save();

        return record;
    }

    private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair) {
        try {
            ECKeyPair keyPair = Curve.generateKeyPair();
            byte[] signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
            SignedPreKeyRecord record = new SignedPreKeyRecord(nextSignedPreKeyId, System.currentTimeMillis(), keyPair, signature);

            signalProtocolStore.storeSignedPreKey(nextSignedPreKeyId, record);
            nextSignedPreKeyId = (nextSignedPreKeyId + 1) % Medium.MAX_VALUE;
            save();

            return record;
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public void verifyAccount(String verificationCode) throws IOException {
        verificationCode = verificationCode.replace("-", "");
        signalingKey = Util.getSecret(52);
        accountManager.verifyAccountWithCode(verificationCode, signalingKey, signalProtocolStore.getLocalRegistrationId(), false, false, true);

        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
        registered = true;

        refreshPreKeys();
        save();
    }

    private void refreshPreKeys() throws IOException {
        List<PreKeyRecord> oneTimePreKeys = generatePreKeys();
        PreKeyRecord lastResortKey = getOrGenerateLastResortPreKey();
        SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey(signalProtocolStore.getIdentityKeyPair());

        accountManager.setPreKeys(signalProtocolStore.getIdentityKeyPair().getPublicKey(), lastResortKey, signedPreKeyRecord, oneTimePreKeys);
    }


    private static List<SignalServiceAttachment> getSignalServiceAttachments(List<String> attachments) throws AttachmentInvalidException {
        List<SignalServiceAttachment> SignalServiceAttachments = null;
        if (attachments != null) {
            SignalServiceAttachments = new ArrayList<>(attachments.size());
            for (String attachment : attachments) {
                try {
                    SignalServiceAttachments.add(createAttachment(new File(attachment)));
                } catch (IOException e) {
                    throw new AttachmentInvalidException(attachment, e);
                }
            }
        }
        return SignalServiceAttachments;
    }

    private static SignalServiceAttachmentStream createAttachment(File attachmentFile) throws IOException {
        InputStream attachmentStream = new FileInputStream(attachmentFile);
        final long attachmentSize = attachmentFile.length();
        String mime = Files.probeContentType(attachmentFile.toPath());
        if (mime == null) {
            mime = "application/octet-stream";
        }
        // TODO mabybe add a parameter to set the voiceNote and preview option
        return new SignalServiceAttachmentStream(attachmentStream, mime, attachmentSize, Optional.of(attachmentFile.getName()), false, Optional.<byte[]>absent(), null);
    }

    private Optional<SignalServiceAttachmentStream> createGroupAvatarAttachment(byte[] groupId) throws IOException {
        File file = getGroupAvatarFile(groupId);
        if (!file.exists()) {
            return Optional.absent();
        }

        return Optional.of(createAttachment(file));
    }

    private Optional<SignalServiceAttachmentStream> createContactAvatarAttachment(String number) throws IOException {
        File file = getContactAvatarFile(number);
        if (!file.exists()) {
            return Optional.absent();
        }

        return Optional.of(createAttachment(file));
    }

    private GroupInfo getGroupForSending(byte[] groupId) throws GroupNotFoundException, NotAGroupMemberException {
        GroupInfo g = groupStore.getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        for (String member : g.members) {
            if (member.equals(this.username)) {
                return g;
            }
        }
        throw new NotAGroupMemberException(groupId, g.name);
    }

    public List<GroupInfo> getGroups() {
        return groupStore.getGroups();
    }

    @Override
    public void sendGroupMessage(String messageText, List<String> attachments,
                                 byte[] groupId)
            throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(getSignalServiceAttachments(attachments));
        }
        if (groupId != null) {
            SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.DELIVER)
                    .withId(groupId)
                    .build();
            messageBuilder.asGroupMessage(group);
        }
        ThreadInfo thread = threadStore.getThread(Base64.encodeBytes(groupId));
        if (thread != null) {
            messageBuilder.withExpiration(thread.messageExpirationTime);
        }

        final GroupInfo g = getGroupForSending(groupId);

        // Don't send group message to ourself
        final List<String> membersSend = new ArrayList<>(g.members);
        membersSend.remove(this.username);
        sendMessage(messageBuilder, membersSend);
    }

    public void sendQuitGroupMessage(byte[] groupId) throws GroupNotFoundException, IOException, EncapsulatedExceptions {
        SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.QUIT)
                .withId(groupId)
                .build();

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group);

        final GroupInfo g = getGroupForSending(groupId);
        g.members.remove(this.username);
        groupStore.updateGroup(g);

        sendMessage(messageBuilder, g.members);
    }

    private static String join(CharSequence separator, Iterable<? extends CharSequence> list) {
        StringBuilder buf = new StringBuilder();
        for (CharSequence str : list) {
            if (buf.length() > 0) {
                buf.append(separator);
            }
            buf.append(str);
        }

        return buf.toString();
    }

    public byte[] sendUpdateGroupMessage(byte[] groupId, String name, Collection<String> members, String avatarFile) throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException {
        GroupInfo g;
        if (groupId == null) {
            // Create new group
            g = new GroupInfo(Util.getSecretBytes(16));
            g.members.add(username);
        } else {
            g = getGroupForSending(groupId);
        }

        if (name != null) {
            g.name = name;
        }

        if (members != null) {
            Set<String> newMembers = new HashSet<>();
            for (String member : members) {
                try {
                    member = canonicalizeNumber(member);
                } catch (InvalidNumberException e) {
                    System.err.println("Failed to add member \"" + member + "\" to group: " + e.getMessage());
                    System.err.println("Aborting…");
                    System.exit(1);
                }
                if (g.members.contains(member)) {
                    continue;
                }
                newMembers.add(member);
                g.members.add(member);
            }
            final List<ContactTokenDetails> contacts = accountManager.getContacts(newMembers);
            if (contacts.size() != newMembers.size()) {
                // Some of the new members are not registered on Signal
                for (ContactTokenDetails contact : contacts) {
                    newMembers.remove(contact.getNumber());
                }
                System.err.println("Failed to add members " + join(", ", newMembers) + " to group: Not registered on Signal");
                System.err.println("Aborting…");
                System.exit(1);
            }
        }

        if (avatarFile != null) {
            createPrivateDirectories(avatarsPath);
            File aFile = getGroupAvatarFile(g.groupId);
            Files.copy(Paths.get(avatarFile), aFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        groupStore.updateGroup(g);

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        // Don't send group message to ourself
        final List<String> membersSend = new ArrayList<>(g.members);
        membersSend.remove(this.username);
        sendMessage(messageBuilder, membersSend);
        return g.groupId;
    }

    private void sendUpdateGroupMessage(byte[] groupId, String recipient) throws IOException, EncapsulatedExceptions {
        if (groupId == null) {
            return;
        }
        GroupInfo g = getGroupForSending(groupId);

        if (!g.members.contains(recipient)) {
            return;
        }

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        // Send group message only to the recipient who requested it
        final List<String> membersSend = new ArrayList<>();
        membersSend.add(recipient);
        sendMessage(messageBuilder, membersSend);
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfo g) {
        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                .withId(g.groupId)
                .withName(g.name)
                .withMembers(new ArrayList<>(g.members));

        File aFile = getGroupAvatarFile(g.groupId);
        if (aFile.exists()) {
            try {
                group.withAvatar(createAttachment(aFile));
            } catch (IOException e) {
                throw new AttachmentInvalidException(aFile.toString(), e);
            }
        }

        return SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build());
    }

    private void sendGroupInfoRequest(byte[] groupId, String recipient) throws IOException, EncapsulatedExceptions {
        if (groupId == null) {
            return;
        }

        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.REQUEST_INFO)
                .withId(groupId);

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build());

        // Send group info request message to the recipient who sent us a message with this groupId
        final List<String> membersSend = new ArrayList<>();
        membersSend.add(recipient);
        sendMessage(messageBuilder, membersSend);
    }

    @Override
    public void sendMessage(String message, List<String> attachments, String recipient)
            throws EncapsulatedExceptions, AttachmentInvalidException, IOException {
        List<String> recipients = new ArrayList<>(1);
        recipients.add(recipient);
        sendMessage(message, attachments, recipients);
    }

    @Override
    public void sendMessage(String messageText, List<String> attachments,
                            List<String> recipients)
            throws IOException, EncapsulatedExceptions, AttachmentInvalidException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(getSignalServiceAttachments(attachments));
        }
        sendMessage(messageBuilder, recipients);
    }

    @Override
    public void sendEndSessionMessage(List<String> recipients) throws IOException, EncapsulatedExceptions {
        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder()
                .asEndSessionMessage();

        sendMessage(messageBuilder, recipients);
    }

    @Override
    public String getContactName(String number) {
        ContactInfo contact = contactStore.getContact(number);
        if (contact == null) {
            return "";
        } else {
            return contact.name;
        }
    }

    @Override
    public void setContactName(String number, String name) {
        ContactInfo contact = contactStore.getContact(number);
        if (contact == null) {
            contact = new ContactInfo();
            contact.number = number;
            System.err.println("Add contact " + number + " named " + name);
        } else {
            System.err.println("Updating contact " + number + " name " + contact.name + " -> " + name);
        }
        contact.name = name;
        contactStore.updateContact(contact);
        save();
    }

    @Override
    public String getGroupName(byte[] groupId) {
        GroupInfo group = getGroup(groupId);
        if (group == null) {
            return "";
        } else {
            return group.name;
        }
    }

    @Override
    public List<String> getGroupMembers(byte[] groupId) {
        GroupInfo group = getGroup(groupId);
        if (group == null) {
            return new ArrayList<String>();
        } else {
            return new ArrayList<String>(group.members);
        }
    }

    @Override
    public byte[] updateGroup(byte[] groupId, String name, List<String> members, String avatar) throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException {
        if (groupId.length == 0) {
            groupId = null;
        }
        if (name.isEmpty()) {
            name = null;
        }
        if (members.size() == 0) {
            members = null;
        }
        if (avatar.isEmpty()) {
            avatar = null;
        }
        return sendUpdateGroupMessage(groupId, name, members, avatar);
    }

    private void requestSyncGroups() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    private void requestSyncContacts() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    private void sendSyncMessage(SignalServiceSyncMessage message)
            throws IOException, UntrustedIdentityException {
        SignalServiceMessageSender messageSender = new SignalServiceMessageSender(serviceUrls, username, password,
                deviceId, signalProtocolStore, USER_AGENT, Optional.fromNullable(messagePipe), Optional.<SignalServiceMessageSender.EventListener>absent());
        try {
            messageSender.sendMessage(message);
        } catch (UntrustedIdentityException e) {
            signalProtocolStore.saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
            throw e;
        }
    }

    private void sendMessage(SignalServiceDataMessage.Builder messageBuilder, Collection<String> recipients)
            throws EncapsulatedExceptions, IOException {
        Set<SignalServiceAddress> recipientsTS = getSignalServiceAddresses(recipients);
        if (recipientsTS == null) return;

        SignalServiceDataMessage message = null;
        try {
            SignalServiceMessageSender messageSender = new SignalServiceMessageSender(serviceUrls, username, password,
                    deviceId, signalProtocolStore, USER_AGENT, Optional.fromNullable(messagePipe), Optional.<SignalServiceMessageSender.EventListener>absent());

            message = messageBuilder.build();
            if (message.getGroupInfo().isPresent()) {
                try {
                    messageSender.sendMessage(new ArrayList<>(recipientsTS), message);
                } catch (EncapsulatedExceptions encapsulatedExceptions) {
                    for (UntrustedIdentityException e : encapsulatedExceptions.getUntrustedIdentityExceptions()) {
                        signalProtocolStore.saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                    }
                }
            } else {
                // Send to all individually, so sync messages are sent correctly
                List<UntrustedIdentityException> untrustedIdentities = new LinkedList<>();
                List<UnregisteredUserException> unregisteredUsers = new LinkedList<>();
                List<NetworkFailureException> networkExceptions = new LinkedList<>();
                for (SignalServiceAddress address : recipientsTS) {
                    ThreadInfo thread = threadStore.getThread(address.getNumber());
                    if (thread != null) {
                        messageBuilder.withExpiration(thread.messageExpirationTime);
                    } else {
                        messageBuilder.withExpiration(0);
                    }
                    message = messageBuilder.build();
                    try {
                        messageSender.sendMessage(address, message);
                    } catch (UntrustedIdentityException e) {
                        signalProtocolStore.saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                        untrustedIdentities.add(e);
                    } catch (UnregisteredUserException e) {
                        unregisteredUsers.add(e);
                    } catch (PushNetworkException e) {
                        networkExceptions.add(new NetworkFailureException(address.getNumber(), e));
                    }
                }
                if (!untrustedIdentities.isEmpty() || !unregisteredUsers.isEmpty() || !networkExceptions.isEmpty()) {
                    throw new EncapsulatedExceptions(untrustedIdentities, unregisteredUsers, networkExceptions);
                }
            }
        } finally {
            if (message != null && message.isEndSession()) {
                for (SignalServiceAddress recipient : recipientsTS) {
                    handleEndSession(recipient.getNumber());
                }
            }
            save();
        }
    }

    private Set<SignalServiceAddress> getSignalServiceAddresses(Collection<String> recipients) {
        Set<SignalServiceAddress> recipientsTS = new HashSet<>(recipients.size());
        for (String recipient : recipients) {
            try {
                recipientsTS.add(getPushAddress(recipient));
            } catch (InvalidNumberException e) {
                System.err.println("Failed to add recipient \"" + recipient + "\": " + e.getMessage());
                System.err.println("Aborting sending.");
                save();
                return null;
            }
        }
        return recipientsTS;
    }

    private SignalServiceContent decryptMessage(SignalServiceEnvelope envelope) throws NoSessionException, LegacyMessageException, InvalidVersionException, InvalidMessageException, DuplicateMessageException, InvalidKeyException, InvalidKeyIdException, org.whispersystems.libsignal.UntrustedIdentityException {
        SignalServiceCipher cipher = new SignalServiceCipher(new SignalServiceAddress(username), signalProtocolStore);
        try {
            return cipher.decrypt(envelope);
        } catch (org.whispersystems.libsignal.UntrustedIdentityException e) {
            signalProtocolStore.saveIdentity(e.getName(), e.getUntrustedIdentity(), TrustLevel.UNTRUSTED);
            throw e;
        }
    }

    private void handleEndSession(String source) {
        signalProtocolStore.deleteAllSessions(source);
    }

    public interface ReceiveMessageHandler {
        void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e);
    }

    private void handleSignalServiceDataMessage(SignalServiceDataMessage message, boolean isSync, String source, String destination, boolean ignoreAttachments) {
        String threadId;
        if (message.getGroupInfo().isPresent()) {
            SignalServiceGroup groupInfo = message.getGroupInfo().get();
            threadId = Base64.encodeBytes(groupInfo.getGroupId());
            GroupInfo group = groupStore.getGroup(groupInfo.getGroupId());
            switch (groupInfo.getType()) {
                case UPDATE:
                    if (group == null) {
                        group = new GroupInfo(groupInfo.getGroupId());
                    }

                    if (groupInfo.getAvatar().isPresent()) {
                        SignalServiceAttachment avatar = groupInfo.getAvatar().get();
                        if (avatar.isPointer()) {
                            try {
                                retrieveGroupAvatarAttachment(avatar.asPointer(), group.groupId);
                            } catch (IOException | InvalidMessageException e) {
                                System.err.println("Failed to retrieve group avatar (" + avatar.asPointer().getId() + "): " + e.getMessage());
                            }
                        }
                    }

                    if (groupInfo.getName().isPresent()) {
                        group.name = groupInfo.getName().get();
                    }

                    if (groupInfo.getMembers().isPresent()) {
                        group.members.addAll(groupInfo.getMembers().get());
                    }

                    groupStore.updateGroup(group);
                    break;
                case DELIVER:
                    if (group == null) {
                        try {
                            sendGroupInfoRequest(groupInfo.getGroupId(), source);
                        } catch (IOException | EncapsulatedExceptions e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case QUIT:
                    if (group == null) {
                        try {
                            sendGroupInfoRequest(groupInfo.getGroupId(), source);
                        } catch (IOException | EncapsulatedExceptions e) {
                            e.printStackTrace();
                        }
                    } else {
                        group.members.remove(source);
                        groupStore.updateGroup(group);
                    }
                    break;
                case REQUEST_INFO:
                    if (group != null) {
                        try {
                            sendUpdateGroupMessage(groupInfo.getGroupId(), source);
                        } catch (IOException | EncapsulatedExceptions e) {
                            e.printStackTrace();
                        } catch (NotAGroupMemberException e) {
                            // We have left this group, so don't send a group update message
                        }
                    }
                    break;
            }
        } else {
            if (isSync) {
                threadId = destination;
            } else {
                threadId = source;
            }
        }
        if (message.isEndSession()) {
            handleEndSession(isSync ? destination : source);
        }
        if (message.isExpirationUpdate() || message.getBody().isPresent()) {
            ThreadInfo thread = threadStore.getThread(threadId);
            if (thread == null) {
                thread = new ThreadInfo();
                thread.id = threadId;
            }
            if (thread.messageExpirationTime != message.getExpiresInSeconds()) {
                thread.messageExpirationTime = message.getExpiresInSeconds();
                threadStore.updateThread(thread);
            }
        }
        if (message.getAttachments().isPresent() && !ignoreAttachments) {
            for (SignalServiceAttachment attachment : message.getAttachments().get()) {
                if (attachment.isPointer()) {
                    try {
                        retrieveAttachment(attachment.asPointer());
                    } catch (IOException | InvalidMessageException e) {
                        System.err.println("Failed to retrieve attachment (" + attachment.asPointer().getId() + "): " + e.getMessage());
                    }
                }
            }
        }
    }

    public void retryFailedReceivedMessages(ReceiveMessageHandler handler, boolean ignoreAttachments) {
        final File cachePath = new File(getMessageCachePath());
        if (!cachePath.exists()) {
            return;
        }
        for (final File dir : cachePath.listFiles()) {
            if (!dir.isDirectory()) {
                continue;
            }

            for (final File fileEntry : dir.listFiles()) {
                if (!fileEntry.isFile()) {
                    continue;
                }
                SignalServiceEnvelope envelope;
                try {
                    envelope = loadEnvelope(fileEntry);
                    if (envelope == null) {
                        continue;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
                SignalServiceContent content = null;
                if (!envelope.isReceipt()) {
                    try {
                        content = decryptMessage(envelope);
                    } catch (Exception e) {
                        continue;
                    }
                    handleMessage(envelope, content, ignoreAttachments);
                }
                save();
                handler.handleMessage(envelope, content, null);
                try {
                    Files.delete(fileEntry.toPath());
                } catch (IOException e) {
                    System.err.println("Failed to delete cached message file “" + fileEntry + "”: " + e.getMessage());
                }
            }
            // Try to delete directory if empty
            dir.delete();
        }
    }

    public void receiveMessages(long timeout, TimeUnit unit, boolean returnOnTimeout, boolean ignoreAttachments, ReceiveMessageHandler handler) throws IOException {
        retryFailedReceivedMessages(handler, ignoreAttachments);
        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(serviceUrls, username, password, deviceId, signalingKey, USER_AGENT);

        try {
            if (messagePipe == null) {
                messagePipe = messageReceiver.createMessagePipe();
            }

            while (true) {
                SignalServiceEnvelope envelope;
                SignalServiceContent content = null;
                Exception exception = null;
                final long now = new Date().getTime();
                try {
                    envelope = messagePipe.read(timeout, unit, new SignalServiceMessagePipe.MessagePipeCallback() {
                        @Override
                        public void onMessage(SignalServiceEnvelope envelope) {
                            // store message on disk, before acknowledging receipt to the server
                            try {
                                File cacheFile = getMessageCacheFile(envelope.getSource(), now, envelope.getTimestamp());
                                storeEnvelope(envelope, cacheFile);
                            } catch (IOException e) {
                                System.err.println("Failed to store encrypted message in disk cache, ignoring: " + e.getMessage());
                            }
                        }
                    });
                } catch (TimeoutException e) {
                    if (returnOnTimeout)
                        return;
                    continue;
                } catch (InvalidVersionException e) {
                    System.err.println("Ignoring error: " + e.getMessage());
                    continue;
                }
                if (!envelope.isReceipt()) {
                    try {
                        content = decryptMessage(envelope);
                    } catch (Exception e) {
                        exception = e;
                    }
                    handleMessage(envelope, content, ignoreAttachments);
                }
                save();
                handler.handleMessage(envelope, content, exception);
                if (exception == null || !(exception instanceof org.whispersystems.libsignal.UntrustedIdentityException)) {
                    File cacheFile = null;
                    try {
                        cacheFile = getMessageCacheFile(envelope.getSource(), now, envelope.getTimestamp());
                        Files.delete(cacheFile.toPath());
                        // Try to delete directory if empty
                        new File(getMessageCachePath()).delete();
                    } catch (IOException e) {
                        System.err.println("Failed to delete cached message file “" + cacheFile + "”: " + e.getMessage());
                    }
                }
            }
        } finally {
            if (messagePipe != null) {
                messagePipe.shutdown();
                messagePipe = null;
            }
        }
    }

    private void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments) {
        if (content != null) {
            if (content.getDataMessage().isPresent()) {
                SignalServiceDataMessage message = content.getDataMessage().get();
                handleSignalServiceDataMessage(message, false, envelope.getSource(), username, ignoreAttachments);
            }
            if (content.getSyncMessage().isPresent()) {
                SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();
                if (syncMessage.getSent().isPresent()) {
                    SignalServiceDataMessage message = syncMessage.getSent().get().getMessage();
                    handleSignalServiceDataMessage(message, true, envelope.getSource(), syncMessage.getSent().get().getDestination().get(), ignoreAttachments);
                }
                if (syncMessage.getRequest().isPresent()) {
                    RequestMessage rm = syncMessage.getRequest().get();
                    if (rm.isContactsRequest()) {
                        try {
                            sendContacts();
                            sendVerifiedMessage();
                        } catch (UntrustedIdentityException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (rm.isGroupsRequest()) {
                        try {
                            sendGroups();
                        } catch (UntrustedIdentityException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (syncMessage.getGroups().isPresent()) {
                    File tmpFile = null;
                    try {
                        tmpFile = Util.createTempFile();
                        try (InputStream attachmentAsStream = retrieveAttachmentAsStream(syncMessage.getGroups().get().asPointer(), tmpFile)) {
                            DeviceGroupsInputStream s = new DeviceGroupsInputStream(attachmentAsStream);
                            DeviceGroup g;
                            while ((g = s.read()) != null) {
                                GroupInfo syncGroup = groupStore.getGroup(g.getId());
                                if (syncGroup == null) {
                                    syncGroup = new GroupInfo(g.getId());
                                }
                                if (g.getName().isPresent()) {
                                    syncGroup.name = g.getName().get();
                                }
                                syncGroup.members.addAll(g.getMembers());
                                syncGroup.active = g.isActive();

                                if (g.getAvatar().isPresent()) {
                                    retrieveGroupAvatarAttachment(g.getAvatar().get(), syncGroup.groupId);
                                }
                                groupStore.updateGroup(syncGroup);
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
                    if (syncMessage.getBlockedList().isPresent()) {
                        // TODO store list of blocked numbers
                    }
                }
                if (syncMessage.getContacts().isPresent()) {
                    File tmpFile = null;
                    try {
                        tmpFile = Util.createTempFile();
                        final ContactsMessage contactsMessage = syncMessage.getContacts().get();
                        try (InputStream attachmentAsStream = retrieveAttachmentAsStream(contactsMessage.getContactsStream().asPointer(), tmpFile)) {
                            DeviceContactsInputStream s = new DeviceContactsInputStream(attachmentAsStream);
                            if (contactsMessage.isComplete()) {
                                contactStore.clear();
                            }
                            DeviceContact c;
                            while ((c = s.read()) != null) {
                                ContactInfo contact = contactStore.getContact(c.getNumber());
                                if (contact == null) {
                                    contact = new ContactInfo();
                                    contact.number = c.getNumber();
                                }
                                if (c.getName().isPresent()) {
                                    contact.name = c.getName().get();
                                }
                                if (c.getColor().isPresent()) {
                                    contact.color = c.getColor().get();
                                }
                                contactStore.updateContact(contact);

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
                    final List<VerifiedMessage> verifiedList = syncMessage.getVerified().get();
                    for (VerifiedMessage v : verifiedList) {
                        signalProtocolStore.saveIdentity(v.getDestination(), v.getIdentityKey(), TrustLevel.fromVerifiedState(v.getVerified()));
                    }
                }
            }
        }
    }

    private SignalServiceEnvelope loadEnvelope(File file) throws IOException {
        try (FileInputStream f = new FileInputStream(file)) {
            DataInputStream in = new DataInputStream(f);
            int version = in.readInt();
            if (version != 1) {
                return null;
            }
            int type = in.readInt();
            String source = in.readUTF();
            int sourceDevice = in.readInt();
            String relay = in.readUTF();
            long timestamp = in.readLong();
            byte[] content = null;
            int contentLen = in.readInt();
            if (contentLen > 0) {
                content = new byte[contentLen];
                in.readFully(content);
            }
            byte[] legacyMessage = null;
            int legacyMessageLen = in.readInt();
            if (legacyMessageLen > 0) {
                legacyMessage = new byte[legacyMessageLen];
                in.readFully(legacyMessage);
            }
            return new SignalServiceEnvelope(type, source, sourceDevice, relay, timestamp, legacyMessage, content);
        }
    }

    private void storeEnvelope(SignalServiceEnvelope envelope, File file) throws IOException {
        try (FileOutputStream f = new FileOutputStream(file)) {
            try (DataOutputStream out = new DataOutputStream(f)) {
                out.writeInt(1); // version
                out.writeInt(envelope.getType());
                out.writeUTF(envelope.getSource());
                out.writeInt(envelope.getSourceDevice());
                out.writeUTF(envelope.getRelay());
                out.writeLong(envelope.getTimestamp());
                if (envelope.hasContent()) {
                    out.writeInt(envelope.getContent().length);
                    out.write(envelope.getContent());
                } else {
                    out.writeInt(0);
                }
                if (envelope.hasLegacyMessage()) {
                    out.writeInt(envelope.getLegacyMessage().length);
                    out.write(envelope.getLegacyMessage());
                } else {
                    out.writeInt(0);
                }
            }
        }
    }

    public File getContactAvatarFile(String number) {
        return new File(avatarsPath, "contact-" + number);
    }

    private File retrieveContactAvatarAttachment(SignalServiceAttachment attachment, String number) throws IOException, InvalidMessageException {
        createPrivateDirectories(avatarsPath);
        if (attachment.isPointer()) {
            SignalServiceAttachmentPointer pointer = attachment.asPointer();
            return retrieveAttachment(pointer, getContactAvatarFile(number), false);
        } else {
            SignalServiceAttachmentStream stream = attachment.asStream();
            return retrieveAttachment(stream, getContactAvatarFile(number));
        }
    }

    public File getGroupAvatarFile(byte[] groupId) {
        return new File(avatarsPath, "group-" + Base64.encodeBytes(groupId).replace("/", "_"));
    }

    private File retrieveGroupAvatarAttachment(SignalServiceAttachment attachment, byte[] groupId) throws IOException, InvalidMessageException {
        createPrivateDirectories(avatarsPath);
        if (attachment.isPointer()) {
            SignalServiceAttachmentPointer pointer = attachment.asPointer();
            return retrieveAttachment(pointer, getGroupAvatarFile(groupId), false);
        } else {
            SignalServiceAttachmentStream stream = attachment.asStream();
            return retrieveAttachment(stream, getGroupAvatarFile(groupId));
        }
    }

    public File getAttachmentFile(long attachmentId) {
        return new File(attachmentsPath, attachmentId + "");
    }

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer) throws IOException, InvalidMessageException {
        createPrivateDirectories(attachmentsPath);
        return retrieveAttachment(pointer, getAttachmentFile(pointer.getId()), true);
    }

    private File retrieveAttachment(SignalServiceAttachmentStream stream, File outputFile) throws IOException, InvalidMessageException {
        InputStream input = stream.getInputStream();

        try (OutputStream output = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[4096];
            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return outputFile;
    }

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer, File outputFile, boolean storePreview) throws IOException, InvalidMessageException {
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

        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(serviceUrls, username, password, deviceId, signalingKey, USER_AGENT);

        File tmpFile = Util.createTempFile();
        try (InputStream input = messageReceiver.retrieveAttachment(pointer, tmpFile, MAX_ATTACHMENT_SIZE)) {
            try (OutputStream output = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[4096];
                int read;

                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        } finally {
            try {
                Files.delete(tmpFile.toPath());
            } catch (IOException e) {
                System.err.println("Failed to delete received attachment temp file “" + tmpFile + "”: " + e.getMessage());
            }
        }
        return outputFile;
    }

    private InputStream retrieveAttachmentAsStream(SignalServiceAttachmentPointer pointer, File tmpFile) throws IOException, InvalidMessageException {
        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(serviceUrls, username, password, deviceId, signalingKey, USER_AGENT);
        return messageReceiver.retrieveAttachment(pointer, tmpFile, MAX_ATTACHMENT_SIZE);
    }

    private String canonicalizeNumber(String number) throws InvalidNumberException {
        String localNumber = username;
        return PhoneNumberFormatter.formatNumber(number, localNumber);
    }

    private SignalServiceAddress getPushAddress(String number) throws InvalidNumberException {
        String e164number = canonicalizeNumber(number);
        return new SignalServiceAddress(e164number);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    private void sendGroups() throws IOException, UntrustedIdentityException {
        File groupsFile = Util.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(groupsFile)) {
                DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(fos);
                for (GroupInfo record : groupStore.getGroups()) {
                    out.write(new DeviceGroup(record.groupId, Optional.fromNullable(record.name),
                            new ArrayList<>(record.members), createGroupAvatarAttachment(record.groupId),
                            record.active));
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

    private void sendContacts() throws IOException, UntrustedIdentityException {
        File contactsFile = Util.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(contactsFile)) {
                DeviceContactsOutputStream out = new DeviceContactsOutputStream(fos);
                for (ContactInfo record : contactStore.getContacts()) {
                    out.write(new DeviceContact(record.number, Optional.fromNullable(record.name),
                            createContactAvatarAttachment(record.number), Optional.fromNullable(record.color)));
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

    private void sendVerifiedMessage() throws IOException, UntrustedIdentityException {
        List<VerifiedMessage> verifiedMessages = new LinkedList<>();
        for (Map.Entry<String, List<JsonIdentityKeyStore.Identity>> x : getIdentities().entrySet()) {
            final String name = x.getKey();
            for (JsonIdentityKeyStore.Identity id : x.getValue()) {
                if (id.getTrustLevel() == TrustLevel.TRUSTED_UNVERIFIED) {
                    continue;
                }
                VerifiedMessage verifiedMessage = new VerifiedMessage(name, id.getIdentityKey(), id.getTrustLevel().toVerifiedState());
                verifiedMessages.add(verifiedMessage);
            }
        }
        sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessages));
    }

    private void sendVerifiedMessage(String destination, IdentityKey identityKey, TrustLevel trustLevel) throws IOException, UntrustedIdentityException {
        VerifiedMessage verifiedMessage = new VerifiedMessage(destination, identityKey, trustLevel.toVerifiedState());
        sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
    }

    public ContactInfo getContact(String number) {
        return contactStore.getContact(number);
    }

    public GroupInfo getGroup(byte[] groupId) {
        return groupStore.getGroup(groupId);
    }

    public Map<String, List<JsonIdentityKeyStore.Identity>> getIdentities() {
        return signalProtocolStore.getIdentities();
    }

    public List<JsonIdentityKeyStore.Identity> getIdentities(String number) {
        return signalProtocolStore.getIdentities(number);
    }

    /**
     * Trust this the identity with this fingerprint
     *
     * @param name        username of the identity
     * @param fingerprint Fingerprint
     */
    public boolean trustIdentityVerified(String name, byte[] fingerprint) {
        List<JsonIdentityKeyStore.Identity> ids = signalProtocolStore.getIdentities(name);
        if (ids == null) {
            return false;
        }
        for (JsonIdentityKeyStore.Identity id : ids) {
            if (!Arrays.equals(id.getIdentityKey().serialize(), fingerprint)) {
                continue;
            }

            signalProtocolStore.saveIdentity(name, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            try {
                sendVerifiedMessage(name, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            } catch (IOException | UntrustedIdentityException e) {
                e.printStackTrace();
            }
            save();
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
    public boolean trustIdentityVerifiedSafetyNumber(String name, String safetyNumber) {
        List<JsonIdentityKeyStore.Identity> ids = signalProtocolStore.getIdentities(name);
        if (ids == null) {
            return false;
        }
        for (JsonIdentityKeyStore.Identity id : ids) {
            if (!safetyNumber.equals(computeSafetyNumber(name, id.getIdentityKey()))) {
                continue;
            }

            signalProtocolStore.saveIdentity(name, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            try {
                sendVerifiedMessage(name, id.getIdentityKey(), TrustLevel.TRUSTED_VERIFIED);
            } catch (IOException | UntrustedIdentityException e) {
                e.printStackTrace();
            }
            save();
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
        List<JsonIdentityKeyStore.Identity> ids = signalProtocolStore.getIdentities(name);
        if (ids == null) {
            return false;
        }
        for (JsonIdentityKeyStore.Identity id : ids) {
            if (id.getTrustLevel() == TrustLevel.UNTRUSTED) {
                signalProtocolStore.saveIdentity(name, id.getIdentityKey(), TrustLevel.TRUSTED_UNVERIFIED);
                try {
                    sendVerifiedMessage(name, id.getIdentityKey(), TrustLevel.TRUSTED_UNVERIFIED);
                } catch (IOException | UntrustedIdentityException e) {
                    e.printStackTrace();
                }
            }
        }
        save();
        return true;
    }

    public String computeSafetyNumber(String theirUsername, IdentityKey theirIdentityKey) {
        Fingerprint fingerprint = new NumericFingerprintGenerator(5200).createFor(username, getIdentity(), theirUsername, theirIdentityKey);
        return fingerprint.getDisplayableFingerprint().getDisplayText();
    }
}
