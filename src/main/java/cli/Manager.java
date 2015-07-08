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

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidVersionException;
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
import org.whispersystems.textsecure.api.messages.TextSecureContent;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.TrustStore;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Manager {
    private final static String URL = "https://textsecure-service.whispersystems.org";
    private final static TrustStore TRUST_STORE = new WhisperTrustStore();

    private final static String settingsPath = System.getProperty("user.home") + "/.config/textsecure";

    private String username;
    private String password;
    private String signalingKey;
    private int preKeyIdOffset;
    private int nextSignedPreKeyId;

    private boolean registered = false;

    private JsonAxolotlStore axolotlStore;
    TextSecureAccountManager accountManager;

    public Manager(String username) {
        this.username = username;
    }

    private String getFileName() {
        String path = settingsPath + "/data";
        new File(path).mkdirs();
        return path + "/" + username;
    }

    public boolean userExists() {
        File f = new File(getFileName());
        if (!f.exists() || f.isDirectory()) {
            return false;
        }
        return true;
    }

    public boolean userHasKeys() {
        return axolotlStore != null;
    }

    public void load() throws IOException, InvalidKeyException {
        JSONObject in = new JSONObject(IOUtils.toString(new FileInputStream(getFileName())));
        username = in.getString("username");
        password = in.getString("password");
        if (in.has("signalingKey")) {
            signalingKey = in.getString("signalingKey");
        }
        if (in.has("preKeyIdOffset")) {
            preKeyIdOffset = in.getInt("preKeyIdOffset");
        } else {
            preKeyIdOffset = 0;
        }
        if (in.has("nextSignedPreKeyId")) {
            nextSignedPreKeyId = in.getInt("nextSignedPreKeyId");
        } else {
            nextSignedPreKeyId = 0;
        }
        axolotlStore = new JsonAxolotlStore(in.getJSONObject("axolotlStore"));
        registered = in.getBoolean("registered");
        accountManager = new TextSecureAccountManager(URL, TRUST_STORE, username, password);
    }

    public void save() {
        String out = new JSONObject().put("username", username)
                .put("password", password)
                .put("signalingKey", signalingKey)
                .put("preKeyIdOffset", preKeyIdOffset)
                .put("nextSignedPreKeyId", nextSignedPreKeyId)
                .put("axolotlStore", axolotlStore.getJson())
                .put("registered", registered).toString();
        try {
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(getFileName()));
            writer.write(out);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            System.out.println("Saving file error: " + e.getMessage());
            return;
        }
    }

    public void createNewIdentity() {
        IdentityKeyPair identityKey = KeyHelper.generateIdentityKeyPair();
        int registrationId = KeyHelper.generateRegistrationId(false);
        axolotlStore = new JsonAxolotlStore(identityKey, registrationId);
        registered = false;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void register(boolean voiceVerication) throws IOException {
        password = Util.getSecret(18);

        accountManager = new TextSecureAccountManager(URL, TRUST_STORE, username, password);

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
        accountManager.verifyAccount(verificationCode, signalingKey, false, axolotlStore.getLocalRegistrationId());

        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
        registered = true;

        List<PreKeyRecord> oneTimePreKeys = generatePreKeys();

        PreKeyRecord lastResortKey = generateLastResortPreKey();

        SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey(axolotlStore.getIdentityKeyPair());

        accountManager.setPreKeys(axolotlStore.getIdentityKeyPair().getPublicKey(), lastResortKey, signedPreKeyRecord, oneTimePreKeys);
    }

    public TextSecureMessageSender getMessageSender() {
        return new TextSecureMessageSender(URL, TRUST_STORE, username, password,
                axolotlStore, Optional.<TextSecureMessageSender.EventListener>absent());
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
        void handleMessage(TextSecureEnvelope envelope);
    }

    public void receiveMessages(int timeoutSeconds, boolean returnOnTimeout, ReceiveMessageHandler handler) throws IOException {
        TextSecureMessageReceiver messageReceiver = new TextSecureMessageReceiver(URL, TRUST_STORE, username, password, signalingKey);
        TextSecureMessagePipe messagePipe = null;

        try {
            messagePipe = messageReceiver.createMessagePipe();

            while (true) {
                TextSecureEnvelope envelope;
                try {
                    envelope = messagePipe.read(timeoutSeconds, TimeUnit.SECONDS);
                    handler.handleMessage(envelope);
                } catch (TimeoutException e) {
                    if (returnOnTimeout)
                        return;
                } catch (InvalidVersionException e) {
                    System.out.println("Ignoring error: " + e.getMessage());
                }
                save();
            }
        } finally {
            if (messagePipe != null)
                messagePipe.shutdown();
        }
    }

    public String canonicalizeNumber(String number) throws InvalidNumberException {
        String localNumber = username;
        return PhoneNumberFormatter.formatNumber(number, localNumber);
    }

    protected TextSecureAddress getPushAddress(String number) throws InvalidNumberException {
        String e164number = canonicalizeNumber(number);
        return new TextSecureAddress(e164number);
    }
}
