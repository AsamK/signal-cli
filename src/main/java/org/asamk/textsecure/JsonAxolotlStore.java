package org.asamk.textsecure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.util.List;

class JsonAxolotlStore implements AxolotlStore {

    @JsonProperty("preKeys")
    @JsonDeserialize(using = JsonPreKeyStore.JsonPreKeyStoreDeserializer.class)
    @JsonSerialize(using = JsonPreKeyStore.JsonPreKeyStoreSerializer.class)
    protected JsonPreKeyStore preKeyStore;

    @JsonProperty("sessionStore")
    @JsonDeserialize(using = JsonSessionStore.JsonSessionStoreDeserializer.class)
    @JsonSerialize(using = JsonSessionStore.JsonPreKeyStoreSerializer.class)
    protected JsonSessionStore sessionStore;

    @JsonProperty("signedPreKeyStore")
    @JsonDeserialize(using = JsonSignedPreKeyStore.JsonSignedPreKeyStoreDeserializer.class)
    @JsonSerialize(using = JsonSignedPreKeyStore.JsonSignedPreKeyStoreSerializer.class)
    protected JsonSignedPreKeyStore signedPreKeyStore;

    @JsonProperty("identityKeyStore")
    @JsonDeserialize(using = JsonIdentityKeyStore.JsonIdentityKeyStoreDeserializer.class)
    @JsonSerialize(using = JsonIdentityKeyStore.JsonIdentityKeyStoreSerializer.class)
    protected JsonIdentityKeyStore identityKeyStore;

    public JsonAxolotlStore() {
    }

    public JsonAxolotlStore(JsonPreKeyStore preKeyStore, JsonSessionStore sessionStore, JsonSignedPreKeyStore signedPreKeyStore, JsonIdentityKeyStore identityKeyStore) {
        this.preKeyStore = preKeyStore;
        this.sessionStore = sessionStore;
        this.signedPreKeyStore = signedPreKeyStore;
        this.identityKeyStore = identityKeyStore;
    }

    public JsonAxolotlStore(IdentityKeyPair identityKeyPair, int registrationId) {
        preKeyStore = new JsonPreKeyStore();
        sessionStore = new JsonSessionStore();
        signedPreKeyStore = new JsonSignedPreKeyStore();
        this.identityKeyStore = new JsonIdentityKeyStore(identityKeyPair, registrationId);
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyStore.getIdentityKeyPair();
    }

    @Override
    public int getLocalRegistrationId() {
        return identityKeyStore.getLocalRegistrationId();
    }

    @Override
    public void saveIdentity(String name, IdentityKey identityKey) {
        identityKeyStore.saveIdentity(name, identityKey);
    }

    @Override
    public boolean isTrustedIdentity(String name, IdentityKey identityKey) {
        return identityKeyStore.isTrustedIdentity(name, identityKey);
    }

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        return preKeyStore.loadPreKey(preKeyId);
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        preKeyStore.storePreKey(preKeyId, record);
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return preKeyStore.containsPreKey(preKeyId);
    }

    @Override
    public void removePreKey(int preKeyId) {
        preKeyStore.removePreKey(preKeyId);
    }

    @Override
    public SessionRecord loadSession(AxolotlAddress address) {
        return sessionStore.loadSession(address);
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        return sessionStore.getSubDeviceSessions(name);
    }

    @Override
    public void storeSession(AxolotlAddress address, SessionRecord record) {
        sessionStore.storeSession(address, record);
    }

    @Override
    public boolean containsSession(AxolotlAddress address) {
        return sessionStore.containsSession(address);
    }

    @Override
    public void deleteSession(AxolotlAddress address) {
        sessionStore.deleteSession(address);
    }

    @Override
    public void deleteAllSessions(String name) {
        sessionStore.deleteAllSessions(name);
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        return signedPreKeyStore.loadSignedPreKey(signedPreKeyId);
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        return signedPreKeyStore.loadSignedPreKeys();
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record);
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        return signedPreKeyStore.containsSignedPreKey(signedPreKeyId);
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        signedPreKeyStore.removeSignedPreKey(signedPreKeyId);
    }
}
