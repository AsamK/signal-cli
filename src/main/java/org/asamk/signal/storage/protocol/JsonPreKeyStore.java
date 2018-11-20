package org.asamk.signal.storage.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class JsonPreKeyStore implements PreKeyStore {

    private final Map<Integer, byte[]> store = new HashMap<>();

    public JsonPreKeyStore() {

    }

    private void addPreKeys(Map<Integer, byte[]> preKeys) {
        store.putAll(preKeys);
    }

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        try {
            if (!store.containsKey(preKeyId)) {
                throw new InvalidKeyIdException("No such prekeyrecord!");
            }

            return new PreKeyRecord(store.get(preKeyId));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        store.put(preKeyId, record.serialize());
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return store.containsKey(preKeyId);
    }

    @Override
    public void removePreKey(int preKeyId) {
        store.remove(preKeyId);
    }

    public static class JsonPreKeyStoreDeserializer extends JsonDeserializer<JsonPreKeyStore> {

        @Override
        public JsonPreKeyStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
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

            JsonPreKeyStore keyStore = new JsonPreKeyStore();
            keyStore.addPreKeys(preKeyMap);

            return keyStore;

        }
    }

    public static class JsonPreKeyStoreSerializer extends JsonSerializer<JsonPreKeyStore> {

        @Override
        public void serialize(JsonPreKeyStore jsonPreKeyStore, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {
            json.writeStartArray();
            for (Map.Entry<Integer, byte[]> preKey : jsonPreKeyStore.store.entrySet()) {
                json.writeStartObject();
                json.writeNumberField("id", preKey.getKey());
                json.writeStringField("record", Base64.encodeBytes(preKey.getValue()));
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }
}
