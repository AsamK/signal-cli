package org.asamk.signal.manager.storage.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

class JsonPreKeyStore implements PreKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(JsonPreKeyStore.class);

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
        public JsonPreKeyStore deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            var preKeyMap = new HashMap<Integer, byte[]>();
            if (node.isArray()) {
                for (var preKey : node) {
                    final var preKeyId = preKey.get("id").asInt();
                    final var preKeyRecord = Base64.getDecoder().decode(preKey.get("record").asText());
                    preKeyMap.put(preKeyId, preKeyRecord);
                }
            }

            var keyStore = new JsonPreKeyStore();
            keyStore.addPreKeys(preKeyMap);

            return keyStore;
        }
    }

    public static class JsonPreKeyStoreSerializer extends JsonSerializer<JsonPreKeyStore> {

        @Override
        public void serialize(
                JsonPreKeyStore jsonPreKeyStore, JsonGenerator json, SerializerProvider serializerProvider
        ) throws IOException {
            json.writeStartArray();
            for (var preKey : jsonPreKeyStore.store.entrySet()) {
                json.writeStartObject();
                json.writeNumberField("id", preKey.getKey());
                json.writeStringField("record", Base64.getEncoder().encodeToString(preKey.getValue()));
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }
}
