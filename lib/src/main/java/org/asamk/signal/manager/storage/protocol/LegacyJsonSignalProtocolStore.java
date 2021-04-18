package org.asamk.signal.manager.storage.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class LegacyJsonSignalProtocolStore {

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
    @JsonDeserialize(using = LegacyJsonIdentityKeyStore.JsonIdentityKeyStoreDeserializer.class)
    private LegacyJsonIdentityKeyStore legacyIdentityKeyStore;

    private LegacyJsonSignalProtocolStore() {
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

    public LegacyJsonIdentityKeyStore getLegacyIdentityKeyStore() {
        return legacyIdentityKeyStore;
    }
}
