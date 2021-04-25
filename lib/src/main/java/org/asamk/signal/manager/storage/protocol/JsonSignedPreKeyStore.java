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
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class JsonSignedPreKeyStore implements SignedPreKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(JsonSignedPreKeyStore.class);

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
            var results = new LinkedList<SignedPreKeyRecord>();

            for (var serialized : store.values()) {
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
        public JsonSignedPreKeyStore deserialize(
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

            var keyStore = new JsonSignedPreKeyStore();
            keyStore.addSignedPreKeys(preKeyMap);

            return keyStore;
        }
    }

    public static class JsonSignedPreKeyStoreSerializer extends JsonSerializer<JsonSignedPreKeyStore> {

        @Override
        public void serialize(
                JsonSignedPreKeyStore jsonPreKeyStore, JsonGenerator json, SerializerProvider serializerProvider
        ) throws IOException {
            json.writeStartArray();
            for (var signedPreKey : jsonPreKeyStore.store.entrySet()) {
                json.writeStartObject();
                json.writeNumberField("id", signedPreKey.getKey());
                json.writeStringField("record", Base64.getEncoder().encodeToString(signedPreKey.getValue()));
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }
}
