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
package cli;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.whispersystems.libaxolotl.*;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.KeyHelper;
import org.whispersystems.libaxolotl.util.Medium;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.TextSecureMessagePipe;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.TextSecureCipher;
import org.whispersystems.textsecure.api.messages.*;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.TrustStore;
import org.whispersystems.textsecure.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class Manager implements TextSecure {
    private final static String URL = "https://textsecure-service.whispersystems.org";
    private final static TrustStore TRUST_STORE = new WhisperTrustStore();

    public final static String PROJECT_NAME = Manager.class.getPackage().getImplementationTitle();
    public final static String PROJECT_VERSION = Manager.class.getPackage().getImplementationVersion();
    private final static String USER_AGENT = PROJECT_NAME + " " + PROJECT_VERSION;

    private final static String settingsPath = System.getProperty("user.home") + "/.config/textsecure";
    private final static String dataPath = settingsPath + "/data";
    private final static String attachmentsPath = settingsPath + "/attachments";

    private final ObjectMapper jsonProcessot = new ObjectMapper();
    private String username;
    private String password;
    private String signalingKey;
    private int preKeyIdOffset;
    private int nextSignedPreKeyId;

    private boolean registered = false;

    private JsonAxolotlStore axolotlStore;
    private TextSecureAccountManager accountManager;
    private JsonGroupStore groupStore;

    public Manager(String username) {
        this.username = username;
        jsonProcessot.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE); // disable autodetect
        jsonProcessot.enable(SerializationFeature.INDENT_OUTPUT); // for pretty print, you can disable it.
        jsonProcessot.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        jsonProcessot.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public String getFileName() {
        new File(dataPath).mkdirs();
        return dataPath + "/" + username;
    }

    public boolean userExists() {
        File f = new File(getFileName());
        return !(!f.exists() || f.isDirectory());
    }

    public boolean userHasKeys() {
        return axolotlStore != null;
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
        axolotlStore = jsonProcessot.convertValue(getNotNullNode(rootNode, "axolotlStore"), JsonAxolotlStore.class); //new JsonAxolotlStore(in.getJSONObject("axolotlStore"));
        registered = getNotNullNode(rootNode, "registered").asBoolean();
        JsonNode groupStoreNode = rootNode.get("groupStore");
        if (groupStoreNode != null) {
            groupStore = jsonProcessot.convertValue(groupStoreNode, JsonGroupStore.class);
        }
        if (groupStore == null) {
            groupStore = new JsonGroupStore();
        }
        accountManager = new TextSecureAccountManager(URL, TRUST_STORE, username, password, USER_AGENT);
    }

    private void save() {
        ObjectNode rootNode = jsonProcessot.createObjectNode();
        rootNode.put("username", username)
                .put("password", password)
                .put("signalingKey", signalingKey)
                .put("preKeyIdOffset", preKeyIdOffset)
                .put("nextSignedPreKeyId", nextSignedPreKeyId)
                .put("registered", registered)
                .putPOJO("axolotlStore", axolotlStore)
                .putPOJO("groupStore", groupStore)
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
        axolotlStore = new JsonAxolotlStore(identityKey, registrationId);
        groupStore = new JsonGroupStore();
        registered = false;
        save();
    }

    public boolean isRegistered() {
        return registered;
    }

    public void register(boolean voiceVerication) throws IOException {
        password = Util.getSecret(18);

        accountManager = new TextSecureAccountManager(URL, TRUST_STORE, username, password, USER_AGENT);

        if (voiceVerication)
            accountManager.requestVoiceVerificationCode();
        else
            accountManager.requestSmsVerificationCode();

        registered = false;
        save();
    }

    private static final int BATCH_SIZE = 100;

    private List<PreKeyRecord> generatePreKeys() {
        List<PreKeyRecord> records = new LinkedList<>();

        for (int i = 0; i < BATCH_SIZE; i++) {
            int preKeyId = (preKeyIdOffset + i) % Medium.MAX_VALUE;
            ECKeyPair keyPair = Curve.generateKeyPair();
            PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

            axolotlStore.storePreKey(preKeyId, record);
            records.add(record);
        }

        preKeyIdOffset = (preKeyIdOffset + BATCH_SIZE + 1) % Medium.MAX_VALUE;
        save();

        return records;
    }

    private PreKeyRecord generateLastResortPreKey() {
        if (axolotlStore.containsPreKey(Medium.MAX_VALUE)) {
            try {
                return axolotlStore.loadPreKey(Medium.MAX_VALUE);
            } catch (InvalidKeyIdException e) {
                axolotlStore.removePreKey(Medium.MAX_VALUE);
            }
        }

        ECKeyPair keyPair = Curve.generateKeyPair();
        PreKeyRecord record = new PreKeyRecord(Medium.MAX_VALUE, keyPair);

        axolotlStore.storePreKey(Medium.MAX_VALUE, record);
        save();

        return record;
    }

    private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair) {
        try {
            ECKeyPair keyPair = Curve.generateKeyPair();
            byte[] signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
            SignedPreKeyRecord record = new SignedPreKeyRecord(nextSignedPreKeyId, System.currentTimeMillis(), keyPair, signature);

            axolotlStore.storeSignedPreKey(nextSignedPreKeyId, record);
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
        accountManager.verifyAccountWithCode(verificationCode, signalingKey, axolotlStore.getLocalRegistrationId(), false);

        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
        registered = true;

        List<PreKeyRecord> oneTimePreKeys = generatePreKeys();

        PreKeyRecord lastResortKey = generateLastResortPreKey();

        SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey(axolotlStore.getIdentityKeyPair());

        accountManager.setPreKeys(axolotlStore.getIdentityKeyPair().getPublicKey(), lastResortKey, signedPreKeyRecord, oneTimePreKeys);
        save();
    }


    private static List<TextSecureAttachment> getTextSecureAttachments(List<String> attachments) throws AttachmentInvalidException {
        List<TextSecureAttachment> textSecureAttachments = null;
        if (attachments != null) {
            textSecureAttachments = new ArrayList<>(attachments.size());
            for (String attachment : attachments) {
                try {
                    textSecureAttachments.add(createAttachment(attachment));
                } catch (IOException e) {
                    throw new AttachmentInvalidException(attachment, e);
                }
            }
        }
        return textSecureAttachments;
    }

    private static TextSecureAttachmentStream createAttachment(String attachment) throws IOException {
        File attachmentFile = new File(attachment);
        InputStream attachmentStream = new FileInputStream(attachmentFile);
        final long attachmentSize = attachmentFile.length();
        String mime = Files.probeContentType(Paths.get(attachment));
        return new TextSecureAttachmentStream(attachmentStream, mime, attachmentSize, null);
    }

    @Override
    public void sendGroupMessage(String messageText, List<String> attachments,
                                 byte[] groupId)
            throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException {
        final TextSecureDataMessage.Builder messageBuilder = TextSecureDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(getTextSecureAttachments(attachments));
        }
        if (groupId != null) {
            TextSecureGroup group = TextSecureGroup.newBuilder(TextSecureGroup.Type.DELIVER)
                    .withId(groupId)
                    .build();
            messageBuilder.asGroupMessage(group);
        }
        TextSecureDataMessage message = messageBuilder.build();

        sendMessage(message, groupStore.getGroup(groupId).members);
    }

    public void sendQuitGroupMessage(byte[] groupId) throws GroupNotFoundException, IOException, EncapsulatedExceptions {
        TextSecureGroup group = TextSecureGroup.newBuilder(TextSecureGroup.Type.QUIT)
                .withId(groupId)
                .build();

        TextSecureDataMessage message = TextSecureDataMessage.newBuilder()
                .asGroupMessage(group)
                .build();

        sendMessage(message, groupStore.getGroup(groupId).members);
    }

    public byte[] sendUpdateGroupMessage(byte[] groupId, String name, Collection<String> members, String avatarFile) throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException {
        GroupInfo g;
        if (groupId == null) {
            // Create new group
            g = new GroupInfo(Util.getSecretBytes(16));
            g.members.add(username);
        } else {
            g = groupStore.getGroup(groupId);
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

        TextSecureGroup.Builder group = TextSecureGroup.newBuilder(TextSecureGroup.Type.UPDATE)
                .withId(g.groupId)
                .withName(g.name)
                .withMembers(new ArrayList<>(g.members));

        if (avatarFile != null) {
            try {
                group.withAvatar(createAttachment(avatarFile));
                // TODO
                g.avatarId = 0;
            } catch (IOException e) {
                throw new AttachmentInvalidException(avatarFile, e);
            }
        }

        groupStore.updateGroup(g);

        TextSecureDataMessage message = TextSecureDataMessage.newBuilder()
                .asGroupMessage(group.build())
                .build();

        sendMessage(message, g.members);
        return g.groupId;
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
        final TextSecureDataMessage.Builder messageBuilder = TextSecureDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(getTextSecureAttachments(attachments));
        }
        TextSecureDataMessage message = messageBuilder.build();

        sendMessage(message, recipients);
    }

    @Override
    public void sendEndSessionMessage(List<String> recipients) throws IOException, EncapsulatedExceptions {
        TextSecureDataMessage message = TextSecureDataMessage.newBuilder()
                .asEndSessionMessage()
                .build();

        sendMessage(message, recipients);
    }

    private void sendMessage(TextSecureDataMessage message, Collection<String> recipients)
            throws IOException, EncapsulatedExceptions {
        TextSecureMessageSender messageSender = new TextSecureMessageSender(URL, TRUST_STORE, username, password,
                axolotlStore, USER_AGENT, Optional.<TextSecureMessageSender.EventListener>absent());

        Set<TextSecureAddress> recipientsTS = new HashSet<>(recipients.size());
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

        messageSender.sendMessage(new ArrayList<>(recipientsTS), message);

        if (message.isEndSession()) {
            for (TextSecureAddress recipient : recipientsTS) {
                handleEndSession(recipient.getNumber());
            }
        }
        save();
    }

    private TextSecureContent decryptMessage(TextSecureEnvelope envelope) {
        TextSecureCipher cipher = new TextSecureCipher(new TextSecureAddress(username), axolotlStore);
        try {
            return cipher.decrypt(envelope);
        } catch (Exception e) {
            // TODO handle all exceptions
            e.printStackTrace();
            return null;
        }
    }

    private void handleEndSession(String source) {
        axolotlStore.deleteAllSessions(source);
    }

    public interface ReceiveMessageHandler {
        void handleMessage(TextSecureEnvelope envelope, TextSecureContent decryptedContent, GroupInfo group);
    }

    public void receiveMessages(int timeoutSeconds, boolean returnOnTimeout, ReceiveMessageHandler handler) throws IOException {
        final TextSecureMessageReceiver messageReceiver = new TextSecureMessageReceiver(URL, TRUST_STORE, username, password, signalingKey, USER_AGENT);
        TextSecureMessagePipe messagePipe = null;

        try {
            messagePipe = messageReceiver.createMessagePipe();

            while (true) {
                TextSecureEnvelope envelope;
                TextSecureContent content = null;
                GroupInfo group = null;
                try {
                    envelope = messagePipe.read(timeoutSeconds, TimeUnit.SECONDS);
                    if (!envelope.isReceipt()) {
                        content = decryptMessage(envelope);
                        if (content != null) {
                            if (content.getDataMessage().isPresent()) {
                                TextSecureDataMessage message = content.getDataMessage().get();
                                if (message.getGroupInfo().isPresent()) {
                                    TextSecureGroup groupInfo = message.getGroupInfo().get();
                                    switch (groupInfo.getType()) {
                                        case UPDATE:
                                            try {
                                                group = groupStore.getGroup(groupInfo.getGroupId());
                                            } catch (GroupNotFoundException e) {
                                                group = new GroupInfo(groupInfo.getGroupId());
                                            }

                                            if (groupInfo.getAvatar().isPresent()) {
                                                TextSecureAttachment avatar = groupInfo.getAvatar().get();
                                                if (avatar.isPointer()) {
                                                    long avatarId = avatar.asPointer().getId();
                                                    try {
                                                        retrieveAttachment(avatar.asPointer());
                                                        group.avatarId = avatarId;
                                                    } catch (IOException | InvalidMessageException e) {
                                                        System.err.println("Failed to retrieve group avatar (" + avatarId + "): " + e.getMessage());
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
                                            try {
                                                group = groupStore.getGroup(groupInfo.getGroupId());
                                            } catch (GroupNotFoundException e) {
                                            }
                                            break;
                                        case QUIT:
                                            try {
                                                group = groupStore.getGroup(groupInfo.getGroupId());
                                                group.members.remove(envelope.getSource());
                                            } catch (GroupNotFoundException e) {
                                            }
                                            break;
                                    }
                                }
                                if (message.isEndSession()) {
                                    handleEndSession(envelope.getSource());
                                }
                                if (message.getAttachments().isPresent()) {
                                    for (TextSecureAttachment attachment : message.getAttachments().get()) {
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
                        }
                    }
                    save();
                    handler.handleMessage(envelope, content, group);
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

    public File getAttachmentFile(long attachmentId) {
        return new File(attachmentsPath + "/" + attachmentId);
    }

    private File retrieveAttachment(TextSecureAttachmentPointer pointer) throws IOException, InvalidMessageException {
        final TextSecureMessageReceiver messageReceiver = new TextSecureMessageReceiver(URL, TRUST_STORE, username, password, signalingKey, USER_AGENT);

        File tmpFile = File.createTempFile("ts_attach_" + pointer.getId(), ".tmp");
        InputStream input = messageReceiver.retrieveAttachment(pointer, tmpFile);

        new File(attachmentsPath).mkdirs();
        File outputFile = getAttachmentFile(pointer.getId());
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
                output = null;
            }
            if (!tmpFile.delete()) {
                System.err.println("Failed to delete temp file: " + tmpFile);
            }
        }
        if (pointer.getPreview().isPresent()) {
            File previewFile = new File(outputFile + ".preview");
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
        return outputFile;
    }

    private String canonicalizeNumber(String number) throws InvalidNumberException {
        String localNumber = username;
        return PhoneNumberFormatter.formatNumber(number, localNumber);
    }

    private TextSecureAddress getPushAddress(String number) throws InvalidNumberException {
        String e164number = canonicalizeNumber(number);
        return new TextSecureAddress(e164number);
    }

    @Override
    public boolean isRemote() {
        return false;
    }
}
