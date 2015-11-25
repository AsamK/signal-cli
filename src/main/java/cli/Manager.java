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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class Manager {
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
        accountManager = new TextSecureAccountManager(URL, TRUST_STORE, username, password, USER_AGENT);
    }

    public void save() {
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

        return record;
    }

    private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair) {
        try {
            ECKeyPair keyPair = Curve.generateKeyPair();
            byte[] signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
            SignedPreKeyRecord record = new SignedPreKeyRecord(nextSignedPreKeyId, System.currentTimeMillis(), keyPair, signature);

            axolotlStore.storeSignedPreKey(nextSignedPreKeyId, record);
            nextSignedPreKeyId = (nextSignedPreKeyId + 1) % Medium.MAX_VALUE;

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
    }

    public void sendMessage(List<TextSecureAddress> recipients, TextSecureDataMessage message)
            throws IOException, EncapsulatedExceptions {
        TextSecureMessageSender messageSender = new TextSecureMessageSender(URL, TRUST_STORE, username, password,
                axolotlStore, USER_AGENT, Optional.<TextSecureMessageSender.EventListener>absent());
        messageSender.sendMessage(recipients, message);
    }

    public TextSecureContent decryptMessage(TextSecureEnvelope envelope) {
        TextSecureCipher cipher = new TextSecureCipher(new TextSecureAddress(username), axolotlStore);
        try {
            return cipher.decrypt(envelope);
        } catch (Exception e) {
            // TODO handle all exceptions
            e.printStackTrace();
            return null;
        }
    }

    public void handleEndSession(String source) {
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
                                            group = new GroupInfo(groupInfo.getGroupId(), groupInfo.getName().get(), groupInfo.getMembers().get(), groupInfo.getAvatar().get().asPointer().getId());
                                            groupStore.updateGroup(group);
                                            break;
                                        case DELIVER:
                                            group = groupStore.getGroup(groupInfo.getGroupId());
                                            break;
                                        case QUIT:
                                            group = groupStore.getGroup(groupInfo.getGroupId());
                                            if (group != null) {
                                                group.members.remove(envelope.getSource());
                                            }
                                            break;
                                    }
                                }
                            }
                        }
                    }
                    handler.handleMessage(envelope, content, group);
                } catch (TimeoutException e) {
                    if (returnOnTimeout)
                        return;
                } catch (InvalidVersionException e) {
                    System.err.println("Ignoring error: " + e.getMessage());
                }
                save();
            }
        } finally {
            if (messagePipe != null)
                messagePipe.shutdown();
        }
    }

    public File retrieveAttachment(TextSecureAttachmentPointer pointer) throws IOException, InvalidMessageException {
        final TextSecureMessageReceiver messageReceiver = new TextSecureMessageReceiver(URL, TRUST_STORE, username, password, signalingKey, USER_AGENT);

        File tmpFile = File.createTempFile("ts_attach_" + pointer.getId(), ".tmp");
        InputStream input = messageReceiver.retrieveAttachment(pointer, tmpFile);

        new File(attachmentsPath).mkdirs();
        File outputFile = new File(attachmentsPath + "/" + pointer.getId());
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

    TextSecureAddress getPushAddress(String number) throws InvalidNumberException {
        String e164number = canonicalizeNumber(number);
        return new TextSecureAddress(e164number);
    }
}
