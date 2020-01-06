package org.asamk.signal.storage.groups;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class GroupInfo {

    @JsonProperty
    public final byte[] groupId;

    @JsonProperty
    public String name;

    @JsonProperty
    public Set<String> members = new HashSet<>();
    @JsonProperty
    public boolean active;
    @JsonProperty
    public String color;
    @JsonProperty(defaultValue = "false")
    public boolean blocked;

    private long avatarId;

    public GroupInfo(byte[] groupId) {
        this.groupId = groupId;
    }

    public GroupInfo(@JsonProperty("groupId") byte[] groupId, @JsonProperty("name") String name, @JsonProperty("members") Collection<String> members, @JsonProperty("avatarId") long avatarId, @JsonProperty("color") String color, @JsonProperty("blocked") boolean blocked) {
        this.groupId = groupId;
        this.name = name;
        this.members.addAll(members);
        this.avatarId = avatarId;
        this.color = color;
        this.blocked = blocked;
    }

    @JsonIgnore
    public long getAvatarId() {
        return avatarId;
    }

    @JsonIgnore
    public Set<SignalServiceAddress> getMembers() {
        Set<SignalServiceAddress> addresses = new HashSet<>(members.size());
        for (String member : members) {
            addresses.add(new SignalServiceAddress(null, member));
        }
        return addresses;
    }

    public void addMembers(Collection<SignalServiceAddress> members) {
        for (SignalServiceAddress member : members) {
            this.members.add(member.getNumber().get());
        }
    }
}
