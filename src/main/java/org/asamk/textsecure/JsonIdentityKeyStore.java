package org.asamk.textsecure;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class JsonIdentityKeyStore implements IdentityKeyStore {

    private final Map<String, IdentityKey> trustedKeys = new HashMap<>();

    private final IdentityKeyPair identityKeyPair;
    private final int localRegistrationId;


    public JsonIdentityKeyStore(IdentityKeyPair identityKeyPair, int localRegistrationId) {
        this.identityKeyPair = identityKeyPair;
        this.localRegistrationId = localRegistrationId;
    }

    public void addTrustedKeys(Map<String, IdentityKey> keyMap) {
        trustedKeys.putAll(keyMap);
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
        trustedKeys.put(name, identityKey);
    }

    @Override
    public boolean isTrustedIdentity(String name, IdentityKey identityKey) {
        IdentityKey trusted = trustedKeys.get(name);
        return (trusted == null || trusted.equals(identityKey));
    }

    public static class JsonIdentityKeyStoreDeserializer extends JsonDeserializer<JsonIdentityKeyStore> {

        @Override
        public JsonIdentityKeyStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            try {
                int localRegistrationId = node.get("registrationId").asInt();
                IdentityKeyPair identityKeyPair = new IdentityKeyPair(Base64.decode(node.get("identityKey").asText()));


                Map<String, IdentityKey> trustedKeyMap = new HashMap<>();
                JsonNode trustedKeysNode = node.get("trustedKeys");
                if (trustedKeysNode.isArray()) {
                    for (JsonNode trustedKey : trustedKeysNode) {
                        String trustedKeyName = trustedKey.get("name").asText();
                        try {
                            trustedKeyMap.put(trustedKeyName, new IdentityKey(Base64.decode(trustedKey.get("identityKey").asText()), 0));
                        } catch (InvalidKeyException | IOException e) {
                            System.out.println(String.format("Error while decoding key for: %s", trustedKeyName));
                        }
                    }
                }

                JsonIdentityKeyStore keyStore = new JsonIdentityKeyStore(identityKeyPair, localRegistrationId);
                keyStore.addTrustedKeys(trustedKeyMap);

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
            for (Map.Entry<String, IdentityKey> trustedKey : jsonIdentityKeyStore.trustedKeys.entrySet()) {
                json.writeStartObject();
                json.writeStringField("name", trustedKey.getKey());
                json.writeStringField("identityKey", Base64.encodeBytes(trustedKey.getValue().serialize()));
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        }
    }
}
