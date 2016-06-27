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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.util.TextUtils;
import org.asamk.Signal;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
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
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class Manager implements Signal {
    private final static String URL = "https://textsecure-service.whispersystems.org";
    private final static TrustStore TRUST_STORE = new WhisperTrustStore();

    public final static String PROJECT_NAME = Manager.class.getPackage().getImplementationTitle();
    public final static String PROJECT_VERSION = Manager.class.getPackage().getImplementationVersion();
    private final static String USER_AGENT = PROJECT_NAME == null ? null : PROJECT_NAME + " " + PROJECT_VERSION;

    private final static int PREKEY_MINIMUM_COUNT = 20;
    private static final int PREKEY_BATCH_SIZE = 100;

    private final String settingsPath;
    private final String dataPath;
    private final String attachmentsPath;
    private final String avatarsPath;

    private final ObjectMapper jsonProcessot = new ObjectMapper();
    private String username;
    private int deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;
    private String password;
    private String signalingKey;
    private int preKeyIdOffset;
    private int nextSignedPreKeyId;

    private boolean registered = false;

    private SignalProtocolStore signalProtocolStore;
    private SignalServiceAccountManager accountManager;
    private JsonGroupStore groupStore;
    private JsonContactsStore contactStore;

    public Manager(String username, String settingsPath) {
        this.username = username;
        this.settingsPath = settingsPath;
        this.dataPath = this.settingsPath + "/data";
        this.attachmentsPath = this.settingsPath + "/attachments";
        this.avatarsPath = this.settingsPath + "/avatars";

        jsonProcessot.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE); // disable autodetect
        jsonProcessot.enable(SerializationFeature.INDENT_OUTPUT); // for pretty print, you can disable it.
        jsonProcessot.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        jsonProcessot.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public String getUsername() {
        return username;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public String getFileName() {
        new File(dataPath).mkdirs();
        return dataPath + "/" + username;
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

    public void load() throws IOException, InvalidKeyException {
        JsonNode rootNode = jsonProcessot.readTree(new File(getFileName()));

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
        signalProtocolStore = jsonProcessot.convertValue(getNotNullNode(rootNode, "axolotlStore"), JsonSignalProtocolStore.class);
        registered = getNotNullNode(rootNode, "registered").asBoolean();
        JsonNode groupStoreNode = rootNode.get("groupStore");
        if (groupStoreNode != null) {
            groupStore = jsonProcessot.convertValue(groupStoreNode, JsonGroupStore.class);
        }
        if (groupStore == null) {
            groupStore = new JsonGroupStore();
        }
        // Copy group avatars that were previously stored in the attachments folder
        // to the new avatar folder
        if (groupStore.groupsWithLegacyAvatarId.size() > 0) {
            for (GroupInfo g : groupStore.groupsWithLegacyAvatarId) {
                File avatarFile = getGroupAvatarFile(g.groupId);
                File attachmentFile = getAttachmentFile(g.getAvatarId());
                if (!avatarFile.exists() && attachmentFile.exists()) {
                    try {
                        new File(avatarsPath).mkdirs();
                        Files.copy(attachmentFile.toPath(), avatarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            groupStore.groupsWithLegacyAvatarId.clear();
            save();
        }

        JsonNode contactStoreNode = rootNode.get("contactStore");
        if (contactStoreNode != null) {
            contactStore = jsonProcessot.convertValue(contactStoreNode, JsonContactsStore.class);
        }
        if (contactStore == null) {
            contactStore = new JsonContactsStore();
        }

        accountManager = new SignalServiceAccountManager(URL, TRUST_STORE, username, password, deviceId, USER_AGENT);
        try {
            if (registered && accountManager.getPreKeysCount() < PREKEY_MINIMUM_COUNT) {
                refreshPreKeys();
                save();
            }
        } catch (AuthorizationFailedException e) {
            System.err.println("Authorization failed, was the number registered elsewhere?");
        }
    }

    private void save() {
        if (username == null) {
            return;
        }
        ObjectNode rootNode = jsonProcessot.createObjectNode();
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
        ;
        try {
            jsonProcessot.writeValue(new File(getFileName()), rootNode);
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

    public void register(boolean voiceVerication) throws IOException {
        password = Util.getSecret(18);

        accountManager = new SignalServiceAccountManager(URL, TRUST_STORE, username, password, USER_AGENT);

        if (voiceVerication)
            accountManager.requestVoiceVerificationCode();
        else
            accountManager.requestSmsVerificationCode();

        registered = false;
        save();
    }

    public URI getDeviceLinkUri() throws TimeoutException, IOException {
        password = Util.getSecret(18);

        accountManager = new SignalServiceAccountManager(URL, TRUST_STORE, username, password, USER_AGENT);
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
        accountManager.verifyAccountWithCode(verificationCode, signalingKey, signalProtocolStore.getLocalRegistrationId(), false, true);

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
        return new SignalServiceAttachmentStream(attachmentStream, mime, attachmentSize, null);
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

    @Override
    public void sendGroupMessage(String messageText, List<String> attachments,
                                 byte[] groupId)
            throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException, UntrustedIdentityException {
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
        SignalServiceDataMessage message = messageBuilder.build();

        GroupInfo g = groupStore.getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        Set<String> members = g.members;
        members.remove(this.username);
        sendMessage(message, members);
    }

    public void sendQuitGroupMessage(byte[] groupId) throws GroupNotFoundException, IOException, EncapsulatedExceptions, UntrustedIdentityException {
        SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.QUIT)
                .withId(groupId)
                .build();

        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group)
                .build();

        final GroupInfo g = groupStore.getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        g.members.remove(this.username);
        groupStore.updateGroup(g);

        sendMessage(message, g.members);
    }

    public byte[] sendUpdateGroupMessage(byte[] groupId, String name, Collection<String> members, String avatarFile) throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException, UntrustedIdentityException {
        GroupInfo g;
        if (groupId == null) {
            // Create new group
            g = new GroupInfo(Util.getSecretBytes(16));
            g.members.add(username);
        } else {
            g = groupStore.getGroup(groupId);
            if (g == null) {
                throw new GroupNotFoundException(groupId);
            }
        }

        if (name != null) {
            g.name = name;
        }

        if (members != null) {
            for (String member : members) {
                try {
                    g.members.add(canonicalizeNumber(member));
                } catch (InvalidNumberException e) {
                    System.err.println("Failed to add member \"" + member + "\" to group: " + e.getMessage());
                    System.err.println("Aborting…");
                    System.exit(1);
                }
            }
        }

        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                .withId(g.groupId)
                .withName(g.name)
                .withMembers(new ArrayList<>(g.members));

        File aFile = getGroupAvatarFile(g.groupId);
        if (avatarFile != null) {
            new File(avatarsPath).mkdirs();
            Files.copy(Paths.get(avatarFile), aFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        if (aFile.exists()) {
            try {
                group.withAvatar(createAttachment(aFile));
            } catch (IOException e) {
                throw new AttachmentInvalidException(avatarFile, e);
            }
        }

        groupStore.updateGroup(g);

        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build())
                .build();

        final List<String> membersSend = new ArrayList<>(g.members);
        membersSend.remove(this.username);
        sendMessage(message, membersSend);
        return g.groupId;
    }

    @Override
    public void sendMessage(String message, List<String> attachments, String recipient)
            throws EncapsulatedExceptions, AttachmentInvalidException, IOException, UntrustedIdentityException {
        List<String> recipients = new ArrayList<>(1);
        recipients.add(recipient);
        sendMessage(message, attachments, recipients);
    }

    @Override
    public void sendMessage(String messageText, List<String> attachments,
                            List<String> recipients)
            throws IOException, EncapsulatedExceptions, AttachmentInvalidException, UntrustedIdentityException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(getSignalServiceAttachments(attachments));
        }
        SignalServiceDataMessage message = messageBuilder.build();

        sendMessage(message, recipients);
    }

    @Override
    public void sendEndSessionMessage(List<String> recipients) throws IOException, EncapsulatedExceptions, UntrustedIdentityException {
        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                .asEndSessionMessage()
                .build();

        sendMessage(message, recipients);
    }

    private void requestSyncGroups() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendMessage(message);
        } catch (EncapsulatedExceptions encapsulatedExceptions) {
            encapsulatedExceptions.printStackTrace();
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    private void requestSyncContacts() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendMessage(message);
        } catch (EncapsulatedExceptions encapsulatedExceptions) {
            encapsulatedExceptions.printStackTrace();
        } catch (UntrustedIdentityException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(SignalServiceSyncMessage message)
            throws IOException, EncapsulatedExceptions, UntrustedIdentityException {
        SignalServiceMessageSender messageSender = new SignalServiceMessageSender(URL, TRUST_STORE, username, password,
                deviceId, signalProtocolStore, USER_AGENT, Optional.<SignalServiceMessageSender.EventListener>absent());
        messageSender.sendMessage(message);
    }

    private void sendMessage(SignalServiceDataMessage message, Collection<String> recipients)
            throws IOException, EncapsulatedExceptions, UntrustedIdentityException {
        try {
            SignalServiceMessageSender messageSender = new SignalServiceMessageSender(URL, TRUST_STORE, username, password,
                    deviceId, signalProtocolStore, USER_AGENT, Optional.<SignalServiceMessageSender.EventListener>absent());

            Set<SignalServiceAddress> recipientsTS = new HashSet<>(recipients.size());
            for (String recipient : recipients) {
                try {
                    recipientsTS.add(getPushAddress(recipient));
                } catch (InvalidNumberException e) {
                    System.err.println("Failed to add recipient \"" + recipient + "\": " + e.getMessage());
                    System.err.println("Aborting sending.");
                    save();
                    return;
                }
            }

            if (message.getGroupInfo().isPresent()) {
                messageSender.sendMessage(new ArrayList<>(recipientsTS), message);
            } else {
                // Send to all individually, so sync messages are sent correctly
                for (SignalServiceAddress address : recipientsTS) {
                    messageSender.sendMessage(address, message);
                }
            }

            if (message.isEndSession()) {
                for (SignalServiceAddress recipient : recipientsTS) {
                    handleEndSession(recipient.getNumber());
                }
            }
        } finally {
            save();
        }
    }

    private SignalServiceContent decryptMessage(SignalServiceEnvelope envelope) {
        SignalServiceCipher cipher = new SignalServiceCipher(new SignalServiceAddress(username), signalProtocolStore);
        try {
            return cipher.decrypt(envelope);
        } catch (Exception e) {
            // TODO handle all exceptions
            e.printStackTrace();
            return null;
        }
    }

    private void handleEndSession(String source) {
        signalProtocolStore.deleteAllSessions(source);
    }

    public interface ReceiveMessageHandler {
        void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent);
    }

    private void handleSignalServiceDataMessage(SignalServiceDataMessage message, boolean isSync, String source, String destination) {
        if (message.getGroupInfo().isPresent()) {
            SignalServiceGroup groupInfo = message.getGroupInfo().get();
            switch (groupInfo.getType()) {
                case UPDATE:
                    GroupInfo group;
                    group = groupStore.getGroup(groupInfo.getGroupId());
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
                    break;
                case QUIT:
                    group = groupStore.getGroup(groupInfo.getGroupId());
                    if (group != null) {
                        group.members.remove(source);
                        groupStore.updateGroup(group);
                    }
                    break;
            }
        }
        if (message.isEndSession()) {
            handleEndSession(isSync ? destination : source);
        }
        if (message.getAttachments().isPresent()) {
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

    public void receiveMessages(int timeoutSeconds, boolean returnOnTimeout, ReceiveMessageHandler handler) throws IOException {
        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(URL, TRUST_STORE, username, password, deviceId, signalingKey, USER_AGENT);
        SignalServiceMessagePipe messagePipe = null;

        try {
            messagePipe = messageReceiver.createMessagePipe();

            while (true) {
                SignalServiceEnvelope envelope;
                SignalServiceContent content = null;
                try {
                    envelope = messagePipe.read(timeoutSeconds, TimeUnit.SECONDS);
                    if (!envelope.isReceipt()) {
                        content = decryptMessage(envelope);
                        if (content != null) {
                            if (content.getDataMessage().isPresent()) {
                                SignalServiceDataMessage message = content.getDataMessage().get();
                                handleSignalServiceDataMessage(message, false, envelope.getSource(), username);
                            }
                            if (content.getSyncMessage().isPresent()) {
                                SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();
                                if (syncMessage.getSent().isPresent()) {
                                    SignalServiceDataMessage message = syncMessage.getSent().get().getMessage();
                                    handleSignalServiceDataMessage(message, true, envelope.getSource(), syncMessage.getSent().get().getDestination().get());
                                }
                                if (syncMessage.getRequest().isPresent()) {
                                    RequestMessage rm = syncMessage.getRequest().get();
                                    if (rm.isContactsRequest()) {
                                        try {
                                            sendContacts();
                                        } catch (EncapsulatedExceptions encapsulatedExceptions) {
                                            encapsulatedExceptions.printStackTrace();
                                        } catch (UntrustedIdentityException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (rm.isGroupsRequest()) {
                                        try {
                                            sendGroups();
                                        } catch (EncapsulatedExceptions encapsulatedExceptions) {
                                            encapsulatedExceptions.printStackTrace();
                                        } catch (UntrustedIdentityException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                if (syncMessage.getGroups().isPresent()) {
                                    try {
                                        DeviceGroupsInputStream s = new DeviceGroupsInputStream(retrieveAttachmentAsStream(syncMessage.getGroups().get().asPointer()));
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
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                if (syncMessage.getContacts().isPresent()) {
                                    try {
                                        DeviceContactsInputStream s = new DeviceContactsInputStream(retrieveAttachmentAsStream(syncMessage.getContacts().get().asPointer()));
                                        DeviceContact c;
                                        while ((c = s.read()) != null) {
                                            ContactInfo contact = new ContactInfo();
                                            contact.number = c.getNumber();
                                            if (c.getName().isPresent()) {
                                                contact.name = c.getName().get();
                                            }
                                            contactStore.updateContact(contact);

                                            if (c.getAvatar().isPresent()) {
                                                retrieveContactAvatarAttachment(c.getAvatar().get(), contact.number);
                                            }
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                    save();
                    handler.handleMessage(envelope, content);
                } catch (TimeoutException e) {
                    if (returnOnTimeout)
                        return;
                } catch (InvalidVersionException e) {
                    System.err.println("Ignoring error: " + e.getMessage());
                }
            }
        } finally {
            if (messagePipe != null)
                messagePipe.shutdown();
        }
    }

    public File getContactAvatarFile(String number) {
        return new File(avatarsPath, "contact-" + number);
    }

    private File retrieveContactAvatarAttachment(SignalServiceAttachment attachment, String number) throws IOException, InvalidMessageException {
        new File(avatarsPath).mkdirs();
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
        new File(avatarsPath).mkdirs();
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
        new File(attachmentsPath).mkdirs();
        return retrieveAttachment(pointer, getAttachmentFile(pointer.getId()), true);
    }

    private File retrieveAttachment(SignalServiceAttachmentStream stream, File outputFile) throws IOException, InvalidMessageException {
        InputStream input = stream.getInputStream();

        OutputStream output = null;
        try {
            output = new FileOutputStream(outputFile);
            byte[] buffer = new byte[4096];
            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (output != null) {
                output.close();
            }
        }
        return outputFile;
    }

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer, File outputFile, boolean storePreview) throws IOException, InvalidMessageException {
        if (storePreview && pointer.getPreview().isPresent()) {
            File previewFile = new File(outputFile + ".preview");
            OutputStream output = null;
            try {
                output = new FileOutputStream(previewFile);
                byte[] preview = pointer.getPreview().get();
                output.write(preview, 0, preview.length);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } finally {
                if (output != null) {
                    output.close();
                }
            }
        }

        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(URL, TRUST_STORE, username, password, deviceId, signalingKey, USER_AGENT);

        File tmpFile = File.createTempFile("ts_attach_" + pointer.getId(), ".tmp");
        InputStream input = messageReceiver.retrieveAttachment(pointer, tmpFile);

        OutputStream output = null;
        try {
            output = new FileOutputStream(outputFile);
            byte[] buffer = new byte[4096];
            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (output != null) {
                output.close();
            }
            if (!tmpFile.delete()) {
                System.err.println("Failed to delete temp file: " + tmpFile);
            }
        }
        return outputFile;
    }

    private InputStream retrieveAttachmentAsStream(SignalServiceAttachmentPointer pointer) throws IOException, InvalidMessageException {
        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(URL, TRUST_STORE, username, password, deviceId, signalingKey, USER_AGENT);
        File file = File.createTempFile("ts_tmp", "tmp");
        file.deleteOnExit();

        return messageReceiver.retrieveAttachment(pointer, file);
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

    private void sendGroups() throws IOException, EncapsulatedExceptions, UntrustedIdentityException {
        File groupsFile = File.createTempFile("multidevice-group-update", ".tmp");

        try {
            DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(new FileOutputStream(groupsFile));
            try {
                for (GroupInfo record : groupStore.getGroups()) {
                    out.write(new DeviceGroup(record.groupId, Optional.fromNullable(record.name),
                            new ArrayList<>(record.members), createGroupAvatarAttachment(record.groupId),
                            record.active));
                }
            } finally {
                out.close();
            }

            if (groupsFile.exists() && groupsFile.length() > 0) {
                FileInputStream contactsFileStream = new FileInputStream(groupsFile);
                SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                        .withStream(contactsFileStream)
                        .withContentType("application/octet-stream")
                        .withLength(groupsFile.length())
                        .build();

                sendMessage(SignalServiceSyncMessage.forGroups(attachmentStream));
            }
        } finally {
            groupsFile.delete();
        }
    }

    private void sendContacts() throws IOException, EncapsulatedExceptions, UntrustedIdentityException {
        File contactsFile = File.createTempFile("multidevice-contact-update", ".tmp");

        try {
            DeviceContactsOutputStream out = new DeviceContactsOutputStream(new FileOutputStream(contactsFile));
            try {
                for (ContactInfo record : contactStore.getContacts()) {
                    out.write(new DeviceContact(record.number, Optional.fromNullable(record.name),
                            createContactAvatarAttachment(record.number)));
                }
            } finally {
                out.close();
            }

            if (contactsFile.exists() && contactsFile.length() > 0) {
                FileInputStream contactsFileStream = new FileInputStream(contactsFile);
                SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                        .withStream(contactsFileStream)
                        .withContentType("application/octet-stream")
                        .withLength(contactsFile.length())
                        .build();

                sendMessage(SignalServiceSyncMessage.forContacts(attachmentStream));
            }
        } finally {
            contactsFile.delete();
        }
    }

    public ContactInfo getContact(String number) {
        return contactStore.getContact(number);
    }

    public GroupInfo getGroup(byte[] groupId) {
        return groupStore.getGroup(groupId);
    }
}
