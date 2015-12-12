package cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JsonGroupStore {
    @JsonProperty("groups")
    @JsonSerialize(using = JsonGroupStore.MapToListSerializer.class)
    @JsonDeserialize(using = JsonGroupStore.GroupsDeserializer.class)
    private Map<String, GroupInfo> groups = new HashMap<>();

    private static final ObjectMapper jsonProcessot = new ObjectMapper();

    void updateGroup(GroupInfo group) {
        groups.put(Base64.encodeBytes(group.groupId), group);
    }

    GroupInfo getGroup(byte[] groupId) throws GroupNotFoundException {
        GroupInfo g = groups.get(Base64.encodeBytes(groupId));
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        return g;
    }

    public static class MapToListSerializer extends JsonSerializer<Map<?, ?>> {
        @Override
        public void serialize(final Map<?, ?> value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
            jgen.writeObject(value.values());
        }
    }

    public static class GroupsDeserializer extends JsonDeserializer<Map<String, GroupInfo>> {
        @Override
        public Map<String, GroupInfo> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            Map<String, GroupInfo> groups = new HashMap<>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (JsonNode n : node) {
                GroupInfo g = jsonProcessot.treeToValue(n, GroupInfo.class);
                groups.put(Base64.encodeBytes(g.groupId), g);
            }

            return groups;
        }
    }
}
