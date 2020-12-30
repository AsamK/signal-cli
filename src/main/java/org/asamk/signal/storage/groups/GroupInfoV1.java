package org.asamk.signal.storage.groups;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

import org.asamk.signal.manager.GroupId;
import org.asamk.signal.manager.GroupIdV1;
import org.asamk.signal.manager.GroupIdV2;
import org.asamk.signal.manager.GroupInviteLinkUrl;
import org.asamk.signal.manager.GroupUtils;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GroupInfoV1 extends GroupInfo {

    private static final ObjectMapper jsonProcessor = new ObjectMapper();

    private final GroupIdV1 groupId;

    private GroupIdV2 expectedV2Id;

    @JsonProperty
    public String name;

    @JsonProperty
    @JsonDeserialize(using = MembersDeserializer.class)
    @JsonSerialize(using = MembersSerializer.class)
    public Set<SignalServiceAddress> members = new HashSet<>();
    @JsonProperty
    public String color;
    @JsonProperty(defaultValue = "0")
    public int messageExpirationTime;
    @JsonProperty(defaultValue = "false")
    public boolean blocked;
    @JsonProperty
    public Integer inboxPosition;
    @JsonProperty(defaultValue = "false")
    public boolean archived;

    public GroupInfoV1(GroupIdV1 groupId) {
        this.groupId = groupId;
    }

    public GroupInfoV1(
            @JsonProperty("groupId") byte[] groupId,
            @JsonProperty("expectedV2Id") byte[] expectedV2Id,
            @JsonProperty("name") String name,
            @JsonProperty("members") Collection<SignalServiceAddress> members,
            @JsonProperty("avatarId") long _ignored_avatarId,
            @JsonProperty("color") String color,
            @JsonProperty("blocked") boolean blocked,
            @JsonProperty("inboxPosition") Integer inboxPosition,
            @JsonProperty("archived") boolean archived,
            @JsonProperty("messageExpirationTime") int messageExpirationTime,
            @JsonProperty("active") boolean _ignored_active
    ) {
        this.groupId = GroupId.v1(groupId);
        this.expectedV2Id = GroupId.v2(expectedV2Id);
        this.name = name;
        this.members.addAll(members);
        this.color = color;
        this.blocked = blocked;
        this.inboxPosition = inboxPosition;
        this.archived = archived;
        this.messageExpirationTime = messageExpirationTime;
    }

    @Override
    @JsonIgnore
    public GroupIdV1 getGroupId() {
        return groupId;
    }

    @JsonProperty("groupId")
    private byte[] getGroupIdJackson() {
        return groupId.serialize();
    }

    @JsonIgnore
    public GroupIdV2 getExpectedV2Id() {
        if (expectedV2Id == null) {
            expectedV2Id = GroupUtils.getGroupIdV2(groupId);
        }
        return expectedV2Id;
    }

    @JsonProperty("expectedV2Id")
    private byte[] getExpectedV2IdJackson() {
        return expectedV2Id.serialize();
    }

    @Override
    public String getTitle() {
        return name;
    }

    @Override
    public GroupInviteLinkUrl getGroupInviteLink() {
        return null;
    }

    @JsonIgnore
    public Set<SignalServiceAddress> getMembers() {
        return members;
    }

    @Override
    public boolean isBlocked() {
        return blocked;
    }

    @Override
    public void setBlocked(final boolean blocked) {
        this.blocked = blocked;
    }

    @Override
    public int getMessageExpirationTime() {
        return messageExpirationTime;
    }

    public void addMembers(Collection<SignalServiceAddress> addresses) {
        for (SignalServiceAddress address : addresses) {
            if (this.members.contains(address)) {
                continue;
            }
            removeMember(address);
            this.members.add(address);
        }
    }

    public void removeMember(SignalServiceAddress address) {
        this.members.removeIf(member -> member.matches(address));
    }

    private static final class JsonSignalServiceAddress {

        @JsonProperty
        private UUID uuid;

        @JsonProperty
        private String number;

        JsonSignalServiceAddress(@JsonProperty("uuid") final UUID uuid, @JsonProperty("number") final String number) {
            this.uuid = uuid;
            this.number = number;
        }

        JsonSignalServiceAddress(SignalServiceAddress address) {
            this.uuid = address.getUuid().orNull();
            this.number = address.getNumber().orNull();
        }

        SignalServiceAddress toSignalServiceAddress() {
            return new SignalServiceAddress(uuid, number);
        }
    }

    private static class MembersSerializer extends JsonSerializer<Set<SignalServiceAddress>> {

        @Override
        public void serialize(
                final Set<SignalServiceAddress> value, final JsonGenerator jgen, final SerializerProvider provider
        ) throws IOException {
            jgen.writeStartArray(value.size());
            for (SignalServiceAddress address : value) {
                if (address.getUuid().isPresent()) {
                    jgen.writeObject(new JsonSignalServiceAddress(address));
                } else {
                    jgen.writeString(address.getNumber().get());
                }
            }
            jgen.writeEndArray();
        }
    }

    private static class MembersDeserializer extends JsonDeserializer<Set<SignalServiceAddress>> {

        @Override
        public Set<SignalServiceAddress> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            Set<SignalServiceAddress> addresses = new HashSet<>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (JsonNode n : node) {
                if (n.isTextual()) {
                    addresses.add(new SignalServiceAddress(null, n.textValue()));
                } else {
                    JsonSignalServiceAddress address = jsonProcessor.treeToValue(n, JsonSignalServiceAddress.class);
                    addresses.add(address.toSignalServiceAddress());
                }
            }

            return addresses;
        }
    }
}
