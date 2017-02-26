package org.asamk.signal.storage.groups;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.asamk.signal.util.Base64;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonGroupStore {
    @JsonProperty("groups")
    @JsonSerialize(using = JsonGroupStore.MapToListSerializer.class)
    @JsonDeserialize(using = JsonGroupStore.GroupsDeserializer.class)
    private Map<String, GroupInfo> groups = new HashMap<>();

    public static List<GroupInfo> groupsWithLegacyAvatarId = new ArrayList<>();

    private static final ObjectMapper jsonProcessor = new ObjectMapper();

    public void updateGroup(GroupInfo group) {
        groups.put(Base64.encodeBytes(group.groupId), group);
    }

    public GroupInfo getGroup(byte[] groupId) {
        GroupInfo g = groups.get(Base64.encodeBytes(groupId));
        return g;
    }

    public List<GroupInfo> getGroups() {
        return new ArrayList<>(groups.values());
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
                GroupInfo g = jsonProcessor.treeToValue(n, GroupInfo.class);
                // Check if a legacy avatarId exists
                if (g.getAvatarId() != 0) {
                    groupsWithLegacyAvatarId.add(g);
                }
                groups.put(Base64.encodeBytes(g.groupId), g);
            }

            return groups;
        }
    }
}
