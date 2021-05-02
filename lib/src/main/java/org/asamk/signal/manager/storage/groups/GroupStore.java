package org.asamk.signal.manager.storage.groups;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdV1;
import org.asamk.signal.manager.groups.GroupIdV2;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.util.IOUtils;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.util.Hex;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GroupStore {

    private final static Logger logger = LoggerFactory.getLogger(GroupStore.class);

    private final File groupCachePath;
    private final Map<GroupId, GroupInfo> groups;
    private final RecipientResolver recipientResolver;
    private final Saver saver;

    private GroupStore(
            final File groupCachePath,
            final Map<GroupId, GroupInfo> groups,
            final RecipientResolver recipientResolver,
            final Saver saver
    ) {
        this.groupCachePath = groupCachePath;
        this.groups = groups;
        this.recipientResolver = recipientResolver;
        this.saver = saver;
    }

    public GroupStore(
            final File groupCachePath, final RecipientResolver recipientResolver, final Saver saver
    ) {
        this.groups = new HashMap<>();
        this.groupCachePath = groupCachePath;
        this.recipientResolver = recipientResolver;
        this.saver = saver;
    }

    public static GroupStore fromStorage(
            final Storage storage,
            final File groupCachePath,
            final RecipientResolver recipientResolver,
            final Saver saver
    ) {
        final var groups = storage.groups.stream().map(g -> {
            if (g instanceof Storage.GroupV1) {
                final var g1 = (Storage.GroupV1) g;
                final var members = g1.members.stream().map(m -> {
                    if (m.recipientId == null) {
                        return recipientResolver.resolveRecipient(new SignalServiceAddress(UuidUtil.parseOrNull(m.uuid),
                                m.number));
                    }

                    return RecipientId.of(m.recipientId);
                }).collect(Collectors.toSet());

                return new GroupInfoV1(GroupIdV1.fromBase64(g1.groupId),
                        g1.expectedV2Id == null ? null : GroupIdV2.fromBase64(g1.expectedV2Id),
                        g1.name,
                        members,
                        g1.color,
                        g1.messageExpirationTime,
                        g1.blocked,
                        g1.archived);
            }

            final var g2 = (Storage.GroupV2) g;
            var groupId = GroupIdV2.fromBase64(g2.groupId);
            GroupMasterKey masterKey;
            try {
                masterKey = new GroupMasterKey(Base64.getDecoder().decode(g2.masterKey));
            } catch (InvalidInputException | IllegalArgumentException e) {
                throw new AssertionError("Invalid master key for group " + groupId.toBase64());
            }

            return new GroupInfoV2(groupId, masterKey, g2.blocked);
        }).collect(Collectors.toMap(GroupInfo::getGroupId, g -> g));

        return new GroupStore(groupCachePath, groups, recipientResolver, saver);
    }

    public void updateGroup(GroupInfo group) {
        final Storage storage;
        synchronized (groups) {
            groups.put(group.getGroupId(), group);
            if (group instanceof GroupInfoV2 && ((GroupInfoV2) group).getGroup() != null) {
                try {
                    IOUtils.createPrivateDirectories(groupCachePath);
                    try (var stream = new FileOutputStream(getGroupV2File(group.getGroupId()))) {
                        ((GroupInfoV2) group).getGroup().writeTo(stream);
                    }
                    final var groupFileLegacy = getGroupV2FileLegacy(group.getGroupId());
                    if (groupFileLegacy.exists()) {
                        groupFileLegacy.delete();
                    }
                } catch (IOException e) {
                    logger.warn("Failed to cache group, ignoring: {}", e.getMessage());
                }
            }
            storage = toStorageLocked();
        }
        saver.save(storage);
    }

    public void deleteGroupV1(GroupIdV1 groupId) {
        final Storage storage;
        synchronized (groups) {
            groups.remove(groupId);
            storage = toStorageLocked();
        }
        saver.save(storage);
    }

    public GroupInfo getGroup(GroupId groupId) {
        synchronized (groups) {
            return getGroupLocked(groupId);
        }
    }

    public GroupInfoV1 getOrCreateGroupV1(GroupIdV1 groupId) {
        synchronized (groups) {
            var group = getGroupLocked(groupId);
            if (group instanceof GroupInfoV1) {
                return (GroupInfoV1) group;
            }

            if (group == null) {
                return new GroupInfoV1(groupId);
            }

            return null;
        }
    }

    public List<GroupInfo> getGroups() {
        synchronized (groups) {
            final var groups = this.groups.values();
            for (var group : groups) {
                loadDecryptedGroupLocked(group);
            }
            return new ArrayList<>(groups);
        }
    }

    public void mergeRecipients(final RecipientId recipientId, final RecipientId toBeMergedRecipientId) {
        synchronized (groups) {
            var modified = false;
            for (var group : this.groups.values()) {
                if (group instanceof GroupInfoV1) {
                    var groupV1 = (GroupInfoV1) group;
                    if (groupV1.isMember(toBeMergedRecipientId)) {
                        groupV1.removeMember(toBeMergedRecipientId);
                        groupV1.addMembers(List.of(recipientId));
                        modified = true;
                    }
                }
            }
            if (modified) {
                saver.save(toStorageLocked());
            }
        }
    }

    private GroupInfo getGroupLocked(final GroupId groupId) {
        var group = groups.get(groupId);
        if (group == null) {
            if (groupId instanceof GroupIdV1) {
                group = getGroupByV1IdLocked((GroupIdV1) groupId);
            } else if (groupId instanceof GroupIdV2) {
                group = getGroupV1ByV2IdLocked((GroupIdV2) groupId);
            }
        }
        loadDecryptedGroupLocked(group);
        return group;
    }

    private GroupInfo getGroupByV1IdLocked(final GroupIdV1 groupId) {
        return groups.get(GroupUtils.getGroupIdV2(groupId));
    }

    private GroupInfoV1 getGroupV1ByV2IdLocked(GroupIdV2 groupIdV2) {
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

    private void loadDecryptedGroupLocked(final GroupInfo group) {
        if (group instanceof GroupInfoV2 && ((GroupInfoV2) group).getGroup() == null) {
            var groupFile = getGroupV2File(group.getGroupId());
            if (!groupFile.exists()) {
                groupFile = getGroupV2FileLegacy(group.getGroupId());
            }
            if (!groupFile.exists()) {
                return;
            }
            try (var stream = new FileInputStream(groupFile)) {
                ((GroupInfoV2) group).setGroup(DecryptedGroup.parseFrom(stream), recipientResolver);
            } catch (IOException ignored) {
            }
        }
    }

    private File getGroupV2FileLegacy(final GroupId groupId) {
        return new File(groupCachePath, Hex.toStringCondensed(groupId.serialize()));
    }

    private File getGroupV2File(final GroupId groupId) {
        return new File(groupCachePath, groupId.toBase64().replace("/", "_"));
    }

    private Storage toStorageLocked() {
        return new Storage(groups.values().stream().map(g -> {
            if (g instanceof GroupInfoV1) {
                final var g1 = (GroupInfoV1) g;
                return new Storage.GroupV1(g1.getGroupId().toBase64(),
                        g1.getExpectedV2Id().toBase64(),
                        g1.name,
                        g1.color,
                        g1.messageExpirationTime,
                        g1.blocked,
                        g1.archived,
                        g1.members.stream()
                                .map(m -> new Storage.GroupV1.Member(m.getId(), null, null))
                                .collect(Collectors.toList()));
            }

            final var g2 = (GroupInfoV2) g;
            return new Storage.GroupV2(g2.getGroupId().toBase64(),
                    Base64.getEncoder().encodeToString(g2.getMasterKey().serialize()),
                    g2.isBlocked());
        }).collect(Collectors.toList()));
    }

    public static class Storage {

        //        @JsonSerialize(using = GroupsSerializer.class)
        @JsonDeserialize(using = GroupsDeserializer.class)
        public List<Storage.Group> groups;

        // For deserialization
        public Storage() {
        }

        public Storage(final List<Storage.Group> groups) {
            this.groups = groups;
        }

        private abstract static class Group {

        }

        private static class GroupV1 extends Group {

            public String groupId;
            public String expectedV2Id;
            public String name;
            public String color;
            public int messageExpirationTime;
            public boolean blocked;
            public boolean archived;

            @JsonDeserialize(using = MembersDeserializer.class)
            @JsonSerialize(using = MembersSerializer.class)
            public List<Member> members;

            // For deserialization
            public GroupV1() {
            }

            public GroupV1(
                    final String groupId,
                    final String expectedV2Id,
                    final String name,
                    final String color,
                    final int messageExpirationTime,
                    final boolean blocked,
                    final boolean archived,
                    final List<Member> members
            ) {
                this.groupId = groupId;
                this.expectedV2Id = expectedV2Id;
                this.name = name;
                this.color = color;
                this.messageExpirationTime = messageExpirationTime;
                this.blocked = blocked;
                this.archived = archived;
                this.members = members;
            }

            private static final class Member {

                public Long recipientId;

                public String uuid;

                public String number;

                Member(Long recipientId, final String uuid, final String number) {
                    this.recipientId = recipientId;
                    this.uuid = uuid;
                    this.number = number;
                }
            }

            private static final class JsonSignalServiceAddress {

                public String uuid;

                public String number;

                // For deserialization
                public JsonSignalServiceAddress() {
                }

                JsonSignalServiceAddress(final String uuid, final String number) {
                    this.uuid = uuid;
                    this.number = number;
                }
            }

            private static class MembersSerializer extends JsonSerializer<List<Member>> {

                @Override
                public void serialize(
                        final List<Member> value, final JsonGenerator jgen, final SerializerProvider provider
                ) throws IOException {
                    jgen.writeStartArray(value.size());
                    for (var address : value) {
                        if (address.recipientId != null) {
                            jgen.writeNumber(address.recipientId);
                        } else if (address.uuid != null) {
                            jgen.writeObject(new JsonSignalServiceAddress(address.uuid, address.number));
                        } else {
                            jgen.writeString(address.number);
                        }
                    }
                    jgen.writeEndArray();
                }
            }

            private static class MembersDeserializer extends JsonDeserializer<List<Member>> {

                @Override
                public List<Member> deserialize(
                        JsonParser jsonParser, DeserializationContext deserializationContext
                ) throws IOException {
                    var addresses = new ArrayList<Member>();
                    JsonNode node = jsonParser.getCodec().readTree(jsonParser);
                    for (var n : node) {
                        if (n.isTextual()) {
                            addresses.add(new Member(null, null, n.textValue()));
                        } else if (n.isNumber()) {
                            addresses.add(new Member(n.numberValue().longValue(), null, null));
                        } else {
                            var address = jsonParser.getCodec().treeToValue(n, JsonSignalServiceAddress.class);
                            addresses.add(new Member(null, address.uuid, address.number));
                        }
                    }

                    return addresses;
                }
            }
        }

        private static class GroupV2 extends Group {

            public String groupId;
            public String masterKey;
            public boolean blocked;

            // For deserialization
            private GroupV2() {
            }

            public GroupV2(final String groupId, final String masterKey, final boolean blocked) {
                this.groupId = groupId;
                this.masterKey = masterKey;
                this.blocked = blocked;
            }
        }

    }

    //    private static class GroupsSerializer extends JsonSerializer<List<Storage.Group>> {
//
//        @Override
//        public void serialize(
//                final List<Storage.Group> groups, final JsonGenerator jgen, final SerializerProvider provider
//        ) throws IOException {
//            jgen.writeStartArray(groups.size());
//            for (var group : groups) {
//                if (group instanceof GroupInfoV1) {
//                    jgen.writeObject(group);
//                } else if (group instanceof GroupInfoV2) {
//                    final var groupV2 = (GroupInfoV2) group;
//                    jgen.writeStartObject();
//                    jgen.writeStringField("groupId", groupV2.getGroupId().toBase64());
//                    jgen.writeStringField("masterKey",
//                            Base64.getEncoder().encodeToString(groupV2.getMasterKey().serialize()));
//                    jgen.writeBooleanField("blocked", groupV2.isBlocked());
//                    jgen.writeEndObject();
//                } else {
//                    throw new AssertionError("Unknown group version");
//                }
//            }
//            jgen.writeEndArray();
//        }
//    }
//
    private static class GroupsDeserializer extends JsonDeserializer<List<Storage.Group>> {

        @Override
        public List<Storage.Group> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            var groups = new ArrayList<Storage.Group>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (var n : node) {
                Storage.Group g;
                if (n.hasNonNull("masterKey")) {
                    // a v2 group
                    g = jsonParser.getCodec().treeToValue(n, Storage.GroupV2.class);
                } else {
                    g = jsonParser.getCodec().treeToValue(n, Storage.GroupV1.class);
                }
                groups.add(g);
            }

            return groups;
        }
    }

    public interface Saver {

        void save(Storage storage);
    }
}
