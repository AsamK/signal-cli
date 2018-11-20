package org.asamk.signal.storage.threads;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonThreadStore {

    private static final ObjectMapper jsonProcessor = new ObjectMapper();

    @JsonProperty("threads")
    @JsonSerialize(using = JsonThreadStore.MapToListSerializer.class)
    @JsonDeserialize(using = ThreadsDeserializer.class)
    private Map<String, ThreadInfo> threads = new HashMap<>();

    public void updateThread(ThreadInfo thread) {
        threads.put(thread.id, thread);
    }

    public ThreadInfo getThread(String id) {
        return threads.get(id);
    }

    public List<ThreadInfo> getThreads() {
        return new ArrayList<>(threads.values());
    }

    private static class MapToListSerializer extends JsonSerializer<Map<?, ?>> {

        @Override
        public void serialize(final Map<?, ?> value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
            jgen.writeObject(value.values());
        }
    }

    private static class ThreadsDeserializer extends JsonDeserializer<Map<String, ThreadInfo>> {

        @Override
        public Map<String, ThreadInfo> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            Map<String, ThreadInfo> threads = new HashMap<>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (JsonNode n : node) {
                ThreadInfo t = jsonProcessor.treeToValue(n, ThreadInfo.class);
                threads.put(t.id, t);
            }

            return threads;
        }
    }
}
