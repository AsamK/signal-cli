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

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GroupInfo {

    private static final ObjectMapper jsonProcessor = new ObjectMapper();

    @JsonProperty
    public final byte[] groupId;

    @JsonProperty
    public String name;

    @JsonProperty
    @JsonDeserialize(using = MembersDeserializer.class)
    @JsonSerialize(using = MembersSerializer.class)
    public Set<SignalServiceAddress> members = new HashSet<>();
    @JsonProperty
    public String color;
    @JsonProperty(defaultValue = "false")
    public boolean blocked;
    @JsonProperty
    public Integer inboxPosition;
    @JsonProperty(defaultValue = "false")
    public boolean archived;

    private long avatarId;

    @JsonProperty
    @JsonIgnore
    private boolean active;

    public GroupInfo(byte[] groupId) {
        this.groupId = groupId;
    }

    public GroupInfo(@JsonProperty("groupId") byte[] groupId, @JsonProperty("name") String name, @JsonProperty("members") Collection<SignalServiceAddress> members, @JsonProperty("avatarId") long avatarId, @JsonProperty("color") String color, @JsonProperty("blocked") boolean blocked, @JsonProperty("inboxPosition") Integer inboxPosition, @JsonProperty("archived") boolean archived) {
        this.groupId = groupId;
        this.name = name;
        this.members.addAll(members);
        this.avatarId = avatarId;
        this.color = color;
        this.blocked = blocked;
        this.inboxPosition = inboxPosition;
        this.archived = archived;
    }

    @JsonIgnore
    public long getAvatarId() {
        return avatarId;
    }

    @JsonIgnore
    public Set<SignalServiceAddress> getMembers() {
        return members;
    }

    @JsonIgnore
    public Set<String> getMembersE164() {
        Set<String> membersE164 = new HashSet<>();
        for (SignalServiceAddress member : members) {
            if (!member.getNumber().isPresent()) {
                continue;
            }
            membersE164.add(member.getNumber().get());
        }
        return membersE164;
    }

    @JsonIgnore
    public Set<SignalServiceAddress> getMembersWithout(SignalServiceAddress address) {
        Set<SignalServiceAddress> members = new HashSet<>(this.members.size());
        for (SignalServiceAddress member : this.members) {
            if (!member.matches(address)) {
                members.add(member);
            }
        }
        return members;
    }

    public void addMembers(Collection<SignalServiceAddress> addresses) {
        for (SignalServiceAddress address : addresses) {
            removeMember(address);
            this.members.add(address);
        }
    }

    public void removeMember(SignalServiceAddress address) {
        this.members.removeIf(member -> member.matches(address));
    }

    @JsonIgnore
    public boolean isMember(SignalServiceAddress address) {
        for (SignalServiceAddress member : this.members) {
            if (member.matches(address)) {
                return true;
            }
        }
        return false;
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
        public void serialize(final Set<SignalServiceAddress> value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
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
        public Set<SignalServiceAddress> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
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
