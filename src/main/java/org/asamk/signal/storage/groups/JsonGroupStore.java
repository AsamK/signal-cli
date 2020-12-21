package org.asamk.signal.storage.groups;

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

import org.asamk.signal.manager.GroupUtils;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.IOUtils;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonGroupStore {

    private static final ObjectMapper jsonProcessor = new ObjectMapper();
    public File groupCachePath;

    @JsonProperty("groups")
    @JsonSerialize(using = GroupsSerializer.class)
    @JsonDeserialize(using = GroupsDeserializer.class)
    private final Map<String, GroupInfo> groups = new HashMap<>();

    private JsonGroupStore() {
    }

    public JsonGroupStore(final File groupCachePath) {
        this.groupCachePath = groupCachePath;
    }

    public void updateGroup(GroupInfo group) {
        groups.put(Base64.encodeBytes(group.groupId), group);
        if (group instanceof GroupInfoV2 && ((GroupInfoV2) group).getGroup() != null) {
            try {
                IOUtils.createPrivateDirectories(groupCachePath);
                try (FileOutputStream stream = new FileOutputStream(getGroupFile(group.groupId))) {
                    ((GroupInfoV2) group).getGroup().writeTo(stream);
                }
            } catch (IOException e) {
                System.err.println("Failed to cache group, ignoring ...");
            }
        }
    }

    public void deleteGroup(byte[] groupId) {
        groups.remove(Base64.encodeBytes(groupId));
    }

    public GroupInfo getGroup(byte[] groupId) {
        final GroupInfo group = groups.get(Base64.encodeBytes(groupId));
        if (group == null & groupId.length == 16) {
            return getGroupByV1Id(groupId);
        }
        loadDecryptedGroup(group);
        return group;
    }

    public GroupInfo getGroupByV1Id(byte[] groupIdV1) {
        GroupInfo group = groups.get(Base64.encodeBytes(groupIdV1));
        if (group == null) {
            group = groups.get(Base64.encodeBytes(GroupUtils.getGroupId(GroupUtils.deriveV2MigrationMasterKey(groupIdV1))));
        }
        loadDecryptedGroup(group);
        return group;
    }

    public GroupInfo getGroupByV2Id(byte[] groupIdV2) {
        GroupInfo group = groups.get(Base64.encodeBytes(groupIdV2));
        if (group == null) {
            for (GroupInfo g : groups.values()) {
                if (g instanceof GroupInfoV1 && Arrays.equals(groupIdV2, ((GroupInfoV1) g).expectedV2Id)) {
                    group = g;
                    break;
                }
            }
        }
        loadDecryptedGroup(group);
        return group;
    }

    private void loadDecryptedGroup(final GroupInfo group) {
        if (group instanceof GroupInfoV2 && ((GroupInfoV2) group).getGroup() == null) {
            try (FileInputStream stream = new FileInputStream(getGroupFile(group.groupId))) {
                ((GroupInfoV2) group).setGroup(DecryptedGroup.parseFrom(stream));
            } catch (IOException ignored) {
            }
        }
    }

    private File getGroupFile(final byte[] groupId) {
        return new File(groupCachePath, Hex.toStringCondensed(groupId));
    }

    public GroupInfoV1 getOrCreateGroupV1(byte[] groupId) {
        GroupInfo group = groups.get(Base64.encodeBytes(groupId));
        if (group instanceof GroupInfoV1) {
            return (GroupInfoV1) group;
        }

        if (group == null) {
            return new GroupInfoV1(groupId);
        }

        return null;
    }

    public List<GroupInfo> getGroups() {
        final Collection<GroupInfo> groups = this.groups.values();
        for (GroupInfo group : groups) {
            loadDecryptedGroup(group);
        }
        return new ArrayList<>(groups);
    }

    private static class GroupsSerializer extends JsonSerializer<Map<String, GroupInfo>> {

        @Override
        public void serialize(
                final Map<String, GroupInfo> value, final JsonGenerator jgen, final SerializerProvider provider
        ) throws IOException {
            final Collection<GroupInfo> groups = value.values();
            jgen.writeStartArray(groups.size());
            for (GroupInfo group : groups) {
                if (group instanceof GroupInfoV1) {
                    jgen.writeObject(group);
                } else if (group instanceof GroupInfoV2) {
                    final GroupInfoV2 groupV2 = (GroupInfoV2) group;
                    jgen.writeStartObject();
                    jgen.writeStringField("groupId", Base64.encodeBytes(groupV2.groupId));
                    jgen.writeStringField("masterKey", Base64.encodeBytes(groupV2.getMasterKey().serialize()));
                    jgen.writeBooleanField("blocked", groupV2.isBlocked());
                    jgen.writeEndObject();
                } else {
                    throw new AssertionError("Unknown group version");
                }
            }
            jgen.writeEndArray();
        }
    }

    private static class GroupsDeserializer extends JsonDeserializer<Map<String, GroupInfo>> {

        @Override
        public Map<String, GroupInfo> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            Map<String, GroupInfo> groups = new HashMap<>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (JsonNode n : node) {
                GroupInfo g;
                if (n.has("masterKey")) {
                    // a v2 group
                    byte[] groupId = Base64.decode(n.get("groupId").asText());
                    try {
                        GroupMasterKey masterKey = new GroupMasterKey(Base64.decode(n.get("masterKey").asText()));
                        g = new GroupInfoV2(groupId, masterKey);
                    } catch (InvalidInputException e) {
                        throw new AssertionError("Invalid master key for group " + Base64.encodeBytes(groupId));
                    }
                    g.setBlocked(n.get("blocked").asBoolean(false));
                } else {
                    GroupInfoV1 gv1 = jsonProcessor.treeToValue(n, GroupInfoV1.class);
                    if (gv1.expectedV2Id == null) {
                        gv1.expectedV2Id = GroupUtils.getGroupId(GroupUtils.deriveV2MigrationMasterKey(gv1.groupId));
                    }
                    g = gv1;
                }
                groups.put(Base64.encodeBytes(g.groupId), g);
            }

            return groups;
        }
    }
}
