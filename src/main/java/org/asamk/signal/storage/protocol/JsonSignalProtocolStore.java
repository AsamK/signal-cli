package org.asamk.signal.storage.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.util.List;
import java.util.Map;

public class JsonSignalProtocolStore implements SignalProtocolStore {

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

    public JsonSignalProtocolStore() {
    }

    public JsonSignalProtocolStore(JsonPreKeyStore preKeyStore, JsonSessionStore sessionStore, JsonSignedPreKeyStore signedPreKeyStore, JsonIdentityKeyStore identityKeyStore) {
        this.preKeyStore = preKeyStore;
        this.sessionStore = sessionStore;
        this.signedPreKeyStore = signedPreKeyStore;
        this.identityKeyStore = identityKeyStore;
    }

    public JsonSignalProtocolStore(IdentityKeyPair identityKeyPair, int registrationId) {
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
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        return identityKeyStore.saveIdentity(address, identityKey);
    }

    public void saveIdentity(String name, IdentityKey identityKey, TrustLevel trustLevel) {
        identityKeyStore.saveIdentity(name, identityKey, trustLevel, null);
    }

    public Map<String, List<JsonIdentityKeyStore.Identity>> getIdentities() {
        return identityKeyStore.getIdentities();
    }

    public List<JsonIdentityKeyStore.Identity> getIdentities(String name) {
        return identityKeyStore.getIdentities(name);
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        return identityKeyStore.isTrustedIdentity(address, identityKey, direction);
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        return identityKeyStore.getIdentity(address);
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
    public SessionRecord loadSession(SignalProtocolAddress address) {
        return sessionStore.loadSession(address);
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        return sessionStore.getSubDeviceSessions(name);
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord record) {
        sessionStore.storeSession(address, record);
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        return sessionStore.containsSession(address);
    }

    @Override
    public void deleteSession(SignalProtocolAddress address) {
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
