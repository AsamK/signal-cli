package org.asamk.signal.manager.storage.threads;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LegacyJsonThreadStore {

    private static final ObjectMapper jsonProcessor = new ObjectMapper();

    @JsonProperty("threads")
    @JsonSerialize(using = MapToListSerializer.class)
    @JsonDeserialize(using = ThreadsDeserializer.class)
    private Map<String, ThreadInfo> threads = new HashMap<>();

    public List<ThreadInfo> getThreads() {
        return new ArrayList<>(threads.values());
    }

    private static class MapToListSerializer extends JsonSerializer<Map<?, ?>> {

        @Override
        public void serialize(
                final Map<?, ?> value, final JsonGenerator jgen, final SerializerProvider provider
        ) throws IOException {
            jgen.writeObject(value.values());
        }
    }

    private static class ThreadsDeserializer extends JsonDeserializer<Map<String, ThreadInfo>> {

        @Override
        public Map<String, ThreadInfo> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            var threads = new HashMap<String, ThreadInfo>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (var n : node) {
                var t = jsonProcessor.treeToValue(n, ThreadInfo.class);
                threads.put(t.id, t);
            }

            return threads;
        }
    }
}
