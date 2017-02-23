package org.asamk.signal.storage.groups;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    private long avatarId;

    @JsonIgnore
    public long getAvatarId() {
        return avatarId;
    }

    @JsonProperty
    public boolean active;

    public GroupInfo(byte[] groupId) {
        this.groupId = groupId;
    }

    public GroupInfo(@JsonProperty("groupId") byte[] groupId, @JsonProperty("name") String name, @JsonProperty("members") Collection<String> members, @JsonProperty("avatarId") long avatarId) {
        this.groupId = groupId;
        this.name = name;
        this.members.addAll(members);
        this.avatarId = avatarId;
    }
}
