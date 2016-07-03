package org.asamk.signal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.state.IdentityKeyStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class JsonIdentityKeyStore implements IdentityKeyStore {

    private final Map<String, List<Identity>> trustedKeys = new HashMap<>();

    private final IdentityKeyPair identityKeyPair;
    private final int localRegistrationId;


    public JsonIdentityKeyStore(IdentityKeyPair identityKeyPair, int localRegistrationId) {
        this.identityKeyPair = identityKeyPair;
        this.localRegistrationId = localRegistrationId;
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyPair;
    }

    @Override
    public int getLocalRegistrationId() {
        return localRegistrationId;
    }

    @Override
    public void saveIdentity(String name, IdentityKey identityKey) {
        saveIdentity(name, identityKey, TrustLevel.TRUSTED_UNVERIFIED);
    }

    public void saveIdentity(String name, IdentityKey identityKey, TrustLevel trustLevel) {
        List<Identity> identities = trustedKeys.get(name);
        if (identities == null) {
            identities = new ArrayList<>();
            trustedKeys.put(name, identities);
        } else {
            for (Identity id : identities) {
                if (!id.identityKey.equals(identityKey))
                    continue;

                id.trustLevel = trustLevel;
                return;
            }
        }
        identities.add(new Identity(identityKey, trustLevel));
    }

    @Override
    public boolean isTrustedIdentity(String name, IdentityKey identityKey) {
        List<Identity> identities = trustedKeys.get(name);
        if (identities == null) {
            // Trust on first use
            return true;
        }

        for (Identity id : identities) {
            if (id.identityKey.equals(identityKey)) {
                return id.isTrusted();
            }
        }

        return false;
    }

    public static class JsonIdentityKeyStoreDeserializer extends JsonDeserializer<JsonIdentityKeyStore> {

        @Override
        public JsonIdentityKeyStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            try {
                int localRegistrationId = node.get("registrationId").asInt();
                IdentityKeyPair identityKeyPair = new IdentityKeyPair(Base64.decode(node.get("identityKey").asText()));


                JsonIdentityKeyStore keyStore = new JsonIdentityKeyStore(identityKeyPair, localRegistrationId);

                JsonNode trustedKeysNode = node.get("trustedKeys");
                if (trustedKeysNode.isArray()) {
                    for (JsonNode trustedKey : trustedKeysNode) {
                        String trustedKeyName = trustedKey.get("name").asText();
                        try {
                            IdentityKey id = new IdentityKey(Base64.decode(trustedKey.get("identityKey").asText()), 0);
                            TrustLevel trustLevel = trustedKey.has("trustLevel") ? TrustLevel.fromInt(trustedKey.get("trustLevel").asInt()) : TrustLevel.TRUSTED_UNVERIFIED;
                            keyStore.saveIdentity(trustedKeyName, id, trustLevel);
                        } catch (InvalidKeyException | IOException e) {
                            System.out.println(String.format("Error while decoding key for: %s", trustedKeyName));
                        }
                    }
                }

                return keyStore;
            } catch (InvalidKeyException e) {
                throw new IOException(e);
            }
        }
    }

    public static class JsonIdentityKeyStoreSerializer extends JsonSerializer<JsonIdentityKeyStore> {

        @Override
        public void serialize(JsonIdentityKeyStore jsonIdentityKeyStore, JsonGenerator json, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
            json.writeStartObject();
            json.writeNumberField("registrationId", jsonIdentityKeyStore.getLocalRegistrationId());
            json.writeStringField("identityKey", Base64.encodeBytes(jsonIdentityKeyStore.getIdentityKeyPair().serialize()));
            json.writeArrayFieldStart("trustedKeys");
            for (Map.Entry<String, List<Identity>> trustedKey : jsonIdentityKeyStore.trustedKeys.entrySet()) {
                for (Identity id : trustedKey.getValue()) {
                    json.writeStartObject();
                    json.writeStringField("name", trustedKey.getKey());
                    json.writeStringField("identityKey", Base64.encodeBytes(id.identityKey.serialize()));
                    json.writeNumberField("trustLevel", id.trustLevel.ordinal());
                    json.writeEndObject();
                }
            }
            json.writeEndArray();
            json.writeEndObject();
        }
    }

    private enum TrustLevel {
        UNTRUSTED,
        TRUSTED_UNVERIFIED,
        TRUSTED_VERIFIED;

        private static TrustLevel[] cachedValues = null;

        public static TrustLevel fromInt(int i) {
            if (TrustLevel.cachedValues == null) {
                TrustLevel.cachedValues = TrustLevel.values();
            }
            return TrustLevel.cachedValues[i];
        }
    }

    private class Identity {
        IdentityKey identityKey;
        TrustLevel trustLevel;

        public Identity(IdentityKey identityKey, TrustLevel trustLevel) {
            this.identityKey = identityKey;
            this.trustLevel = trustLevel;
        }

        public boolean isTrusted() {
            return trustLevel == TrustLevel.TRUSTED_UNVERIFIED ||
                    trustLevel == TrustLevel.TRUSTED_VERIFIED;
        }
    }
}
