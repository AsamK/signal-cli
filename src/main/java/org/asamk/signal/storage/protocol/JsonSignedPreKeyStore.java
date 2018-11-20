package org.asamk.signal.storage.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class JsonSignedPreKeyStore implements SignedPreKeyStore {

    private final Map<Integer, byte[]> store = new HashMap<>();

    public JsonSignedPreKeyStore() {

    }

    private void addSignedPreKeys(Map<Integer, byte[]> preKeys) {
        store.putAll(preKeys);
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        try {
            if (!store.containsKey(signedPreKeyId)) {
                throw new InvalidKeyIdException("No such signedprekeyrecord! " + signedPreKeyId);
            }

            return new SignedPreKeyRecord(store.get(signedPreKeyId));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        try {
            List<SignedPreKeyRecord> results = new LinkedList<>();

            for (byte[] serialized : store.values()) {
                results.add(new SignedPreKeyRecord(serialized));
            }

            return results;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        store.put(signedPreKeyId, record.serialize());
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        return store.containsKey(signedPreKeyId);
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        store.remove(signedPreKeyId);
    }

    public static class JsonSignedPreKeyStoreDeserializer extends JsonDeserializer<JsonSignedPreKeyStore> {

        @Override
        public JsonSignedPreKeyStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            Map<Integer, byte[]> preKeyMap = new HashMap<>();
            if (node.isArray()) {
                for (JsonNode preKey : node) {
                    Integer preKeyId = preKey.get("id").asInt();
                    try {
                        preKeyMap.put(preKeyId, Base64.decode(preKey.get("record").asText()));
                    } catch (IOException e) {
                        System.out.println(String.format("Error while decoding prekey for: %s", preKeyId));
                    }
                }
            }

            JsonSignedPreKeyStore keyStore = new JsonSignedPreKeyStore();
            keyStore.addSignedPreKeys(preKeyMap);

            return keyStore;

        }
    }

    public static class JsonSignedPreKeyStoreSerializer extends JsonSerializer<JsonSignedPreKeyStore> {

        @Override
        public void serialize(JsonSignedPreKeyStore jsonPreKeyStore, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {
            json.writeStartArray();
            for (Map.Entry<Integer, byte[]> signedPreKey : jsonPreKeyStore.store.entrySet()) {
                json.writeStartObject();
                json.writeNumberField("id", signedPreKey.getKey());
                json.writeStringField("record", Base64.encodeBytes(signedPreKey.getValue()));
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }
}
