package org.asamk.signal.manager.storage.groups;

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

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdV1;
import org.asamk.signal.manager.groups.GroupIdV2;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.util.IOUtils;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.util.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonGroupStore {

    private final static Logger logger = LoggerFactory.getLogger(JsonGroupStore.class);

    private static final ObjectMapper jsonProcessor = new ObjectMapper();
    public File groupCachePath;

    @JsonProperty("groups")
    @JsonSerialize(using = GroupsSerializer.class)
    @JsonDeserialize(using = GroupsDeserializer.class)
    private final Map<GroupId, GroupInfo> groups = new HashMap<>();

    private JsonGroupStore() {
    }

    public JsonGroupStore(final File groupCachePath) {
        this.groupCachePath = groupCachePath;
    }

    public void updateGroup(GroupInfo group) {
        groups.put(group.getGroupId(), group);
        if (group instanceof GroupInfoV2 && ((GroupInfoV2) group).getGroup() != null) {
            try {
                IOUtils.createPrivateDirectories(groupCachePath);
                try (var stream = new FileOutputStream(getGroupFile(group.getGroupId()))) {
                    ((GroupInfoV2) group).getGroup().writeTo(stream);
                }
                final var groupFileLegacy = getGroupFileLegacy(group.getGroupId());
                if (groupFileLegacy.exists()) {
                    groupFileLegacy.delete();
                }
            } catch (IOException e) {
                logger.warn("Failed to cache group, ignoring: {}", e.getMessage());
            }
        }
    }

    public void deleteGroup(GroupId groupId) {
        groups.remove(groupId);
    }

    public GroupInfo getGroup(GroupId groupId) {
        var group = groups.get(groupId);
        if (group == null) {
            if (groupId instanceof GroupIdV1) {
                group = groups.get(GroupUtils.getGroupIdV2((GroupIdV1) groupId));
            } else if (groupId instanceof GroupIdV2) {
                group = getGroupV1ByV2Id((GroupIdV2) groupId);
            }
        }
        loadDecryptedGroup(group);
        return group;
    }

    private GroupInfoV1 getGroupV1ByV2Id(GroupIdV2 groupIdV2) {
        for (var g : groups.values()) {
            if (g instanceof GroupInfoV1) {
                final var gv1 = (GroupInfoV1) g;
                if (groupIdV2.equals(gv1.getExpectedV2Id())) {
                    return gv1;
                }
            }
        }
        return null;
    }

    private void loadDecryptedGroup(final GroupInfo group) {
        if (group instanceof GroupInfoV2 && ((GroupInfoV2) group).getGroup() == null) {
            var groupFile = getGroupFile(group.getGroupId());
            if (!groupFile.exists()) {
                groupFile = getGroupFileLegacy(group.getGroupId());
            }
            if (!groupFile.exists()) {
                return;
            }
            try (var stream = new FileInputStream(groupFile)) {
                ((GroupInfoV2) group).setGroup(DecryptedGroup.parseFrom(stream));
            } catch (IOException ignored) {
            }
        }
    }

    private File getGroupFileLegacy(final GroupId groupId) {
        return new File(groupCachePath, Hex.toStringCondensed(groupId.serialize()));
    }

    private File getGroupFile(final GroupId groupId) {
        return new File(groupCachePath, groupId.toBase64().replace("/", "_"));
    }

    public GroupInfoV1 getOrCreateGroupV1(GroupIdV1 groupId) {
        var group = getGroup(groupId);
        if (group instanceof GroupInfoV1) {
            return (GroupInfoV1) group;
        }

        if (group == null) {
            return new GroupInfoV1(groupId);
        }

        return null;
    }

    public List<GroupInfo> getGroups() {
        final var groups = this.groups.values();
        for (var group : groups) {
            loadDecryptedGroup(group);
        }
        return new ArrayList<>(groups);
    }

    private static class GroupsSerializer extends JsonSerializer<Map<String, GroupInfo>> {

        @Override
        public void serialize(
                final Map<String, GroupInfo> value, final JsonGenerator jgen, final SerializerProvider provider
        ) throws IOException {
            final var groups = value.values();
            jgen.writeStartArray(groups.size());
            for (var group : groups) {
                if (group instanceof GroupInfoV1) {
                    jgen.writeObject(group);
                } else if (group instanceof GroupInfoV2) {
                    final var groupV2 = (GroupInfoV2) group;
                    jgen.writeStartObject();
                    jgen.writeStringField("groupId", groupV2.getGroupId().toBase64());
                    jgen.writeStringField("masterKey",
                            Base64.getEncoder().encodeToString(groupV2.getMasterKey().serialize()));
                    jgen.writeBooleanField("blocked", groupV2.isBlocked());
                    jgen.writeEndObject();
                } else {
                    throw new AssertionError("Unknown group version");
                }
            }
            jgen.writeEndArray();
        }
    }

    private static class GroupsDeserializer extends JsonDeserializer<Map<GroupId, GroupInfo>> {

        @Override
        public Map<GroupId, GroupInfo> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            var groups = new HashMap<GroupId, GroupInfo>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (var n : node) {
                GroupInfo g;
                if (n.hasNonNull("masterKey")) {
                    // a v2 group
                    var groupId = GroupIdV2.fromBase64(n.get("groupId").asText());
                    try {
                        var masterKey = new GroupMasterKey(Base64.getDecoder().decode(n.get("masterKey").asText()));
                        g = new GroupInfoV2(groupId, masterKey);
                    } catch (InvalidInputException | IllegalArgumentException e) {
                        throw new AssertionError("Invalid master key for group " + groupId.toBase64());
                    }
                    g.setBlocked(n.get("blocked").asBoolean(false));
                } else {
                    g = jsonProcessor.treeToValue(n, GroupInfoV1.class);
                }
                groups.put(g.getGroupId(), g);
            }

            return groups;
        }
    }
}
