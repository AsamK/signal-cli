package org.asamk.signal.manager.storage.protocol;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class LegacyJsonPreKeyStore {

    private final Map<Integer, byte[]> preKeys;

    private LegacyJsonPreKeyStore(final Map<Integer, byte[]> preKeys) {
        this.preKeys = preKeys;
    }

    public Map<Integer, byte[]> getPreKeys() {
        return preKeys;
    }

    public static class JsonPreKeyStoreDeserializer extends JsonDeserializer<LegacyJsonPreKeyStore> {

        @Override
        public LegacyJsonPreKeyStore deserialize(
                JsonParser jsonParser,
                DeserializationContext deserializationContext
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

            return new LegacyJsonPreKeyStore(preKeyMap);
        }
    }
}
