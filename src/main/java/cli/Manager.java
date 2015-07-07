/**
 * Copyright (C) 2015 AsamK
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cli;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidVersionException;
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

import java.io.*;
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
        axolotlStore = new JsonAxolotlStore(in.getJSONObject("axolotlStore"));
        registered = in.getBoolean("registered");
        accountManager = new TextSecureAccountManager(URL, TRUST_STORE, username, password);
    }

    public void save() {
        String out = new JSONObject().put("username", username)
                .put("password", password)
                .put("signalingKey", signalingKey)
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

    public void verifyAccount(String verificationCode) throws IOException {
        verificationCode = verificationCode.replace("-", "");
        signalingKey = Util.getSecret(52);
        accountManager.verifyAccount(verificationCode, signalingKey, false, axolotlStore.getLocalRegistrationId());

        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
        registered = true;

        int start = 0;
        List<PreKeyRecord> oneTimePreKeys = KeyHelper.generatePreKeys(start, 100);
        for (int i = start; i < oneTimePreKeys.size(); i++) {
            axolotlStore.storePreKey(i, oneTimePreKeys.get(i));
        }

        PreKeyRecord lastResortKey = KeyHelper.generateLastResortPreKey();
        axolotlStore.storePreKey(Medium.MAX_VALUE, lastResortKey);

        int signedPreKeyId = 0;
        SignedPreKeyRecord signedPreKeyRecord;
        try {
            signedPreKeyRecord = KeyHelper.generateSignedPreKey(axolotlStore.getIdentityKeyPair(), signedPreKeyId);
            axolotlStore.storeSignedPreKey(signedPreKeyId, signedPreKeyRecord);
        } catch (InvalidKeyException e) {
            // Should really not happen
            System.out.println("invalid key");
            return;
        }
        accountManager.setPreKeys(axolotlStore.getIdentityKeyPair().getPublicKey(), lastResortKey, signedPreKeyRecord, oneTimePreKeys);
    }

    public TextSecureMessageSender getMessageSender() {
        return new TextSecureMessageSender(URL, TRUST_STORE, username, password,
                axolotlStore, Optional.<TextSecureMessageSender.EventListener>absent());
    }

    public TextSecureContent receiveMessage() throws IOException, InvalidVersionException {
        TextSecureMessageReceiver messageReceiver = new TextSecureMessageReceiver(URL, TRUST_STORE, username, password, signalingKey);
        TextSecureMessagePipe messagePipe = null;

        try {
            messagePipe = messageReceiver.createMessagePipe();

            TextSecureEnvelope envelope;
            try {
                envelope = messagePipe.read(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                return null;
            }
            TextSecureCipher cipher = new TextSecureCipher(new TextSecureAddress(username), axolotlStore);
            TextSecureContent message = null;
            try {
                message = cipher.decrypt(envelope);
            } catch (Exception e) {
                // TODO handle all exceptions
                e.printStackTrace();
            }
            return message;
        } finally {
            if (messagePipe != null)
                messagePipe.shutdown();
        }
    }
}
