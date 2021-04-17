package org.asamk.signal.manager.storage.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.asamk.signal.manager.TrustLevel;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.signalservice.api.SignalServiceProtocolStore;
import org.whispersystems.signalservice.api.SignalServiceSessionStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

@JsonIgnoreProperties(value = {"sessionStore", "preKeys", "signedPreKeyStore"}, allowSetters = true)
public class JsonSignalProtocolStore implements SignalServiceProtocolStore {

    @JsonProperty("preKeys")
    @JsonDeserialize(using = LegacyJsonPreKeyStore.JsonPreKeyStoreDeserializer.class)
    private LegacyJsonPreKeyStore legacyPreKeyStore;

    @JsonProperty("sessionStore")
    @JsonDeserialize(using = LegacyJsonSessionStore.JsonSessionStoreDeserializer.class)
    private LegacyJsonSessionStore legacySessionStore;

    @JsonProperty("signedPreKeyStore")
    @JsonDeserialize(using = LegacyJsonSignedPreKeyStore.JsonSignedPreKeyStoreDeserializer.class)
    private LegacyJsonSignedPreKeyStore legacySignedPreKeyStore;

    @JsonProperty("identityKeyStore")
    @JsonDeserialize(using = JsonIdentityKeyStore.JsonIdentityKeyStoreDeserializer.class)
    @JsonSerialize(using = JsonIdentityKeyStore.JsonIdentityKeyStoreSerializer.class)
    private JsonIdentityKeyStore identityKeyStore;

    private PreKeyStore preKeyStore;
    private SignedPreKeyStore signedPreKeyStore;
    private SignalServiceSessionStore sessionStore;

    public JsonSignalProtocolStore() {
    }

    public JsonSignalProtocolStore(
            IdentityKeyPair identityKeyPair,
            int registrationId,
            PreKeyStore preKeyStore,
            SignedPreKeyStore signedPreKeyStore,
            SignalServiceSessionStore sessionStore
    ) {
        this.preKeyStore = preKeyStore;
        this.signedPreKeyStore = signedPreKeyStore;
        this.sessionStore = sessionStore;
        this.identityKeyStore = new JsonIdentityKeyStore(identityKeyPair, registrationId);
    }

    public void setResolver(final SignalServiceAddressResolver resolver) {
        identityKeyStore.setResolver(resolver);
    }

    public void setPreKeyStore(final PreKeyStore preKeyStore) {
        this.preKeyStore = preKeyStore;
    }

    public void setSignedPreKeyStore(final SignedPreKeyStore signedPreKeyStore) {
        this.signedPreKeyStore = signedPreKeyStore;
    }

    public void setSessionStore(final SignalServiceSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public LegacyJsonPreKeyStore getLegacyPreKeyStore() {
        return legacyPreKeyStore;
    }

    public LegacyJsonSignedPreKeyStore getLegacySignedPreKeyStore() {
        return legacySignedPreKeyStore;
    }

    public LegacyJsonSessionStore getLegacySessionStore() {
        return legacySessionStore;
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

    public void saveIdentity(SignalServiceAddress serviceAddress, IdentityKey identityKey, TrustLevel trustLevel) {
        identityKeyStore.saveIdentity(serviceAddress, identityKey, trustLevel, null);
    }

    public void setIdentityTrustLevel(
            SignalServiceAddress serviceAddress, IdentityKey identityKey, TrustLevel trustLevel
    ) {
        identityKeyStore.setIdentityTrustLevel(serviceAddress, identityKey, trustLevel);
    }

    public void removeIdentity(SignalServiceAddress serviceAddress, IdentityKey identityKey) {
        identityKeyStore.removeIdentity(serviceAddress, identityKey);
    }

    public List<IdentityInfo> getIdentities() {
        return identityKeyStore.getIdentities();
    }

    public List<IdentityInfo> getIdentities(SignalServiceAddress serviceAddress) {
        return identityKeyStore.getIdentities(serviceAddress);
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        return identityKeyStore.isTrustedIdentity(address, identityKey, direction);
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        return identityKeyStore.getIdentity(address);
    }

    public IdentityInfo getIdentity(SignalServiceAddress serviceAddress) {
        return identityKeyStore.getIdentity(serviceAddress);
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
    public void archiveSession(final SignalProtocolAddress address) {
        sessionStore.archiveSession(address);
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
