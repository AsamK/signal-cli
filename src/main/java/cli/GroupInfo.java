package cli;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GroupInfo {
    @JsonProperty
    public final byte[] groupId;

    @JsonProperty
    public String name;

    @JsonProperty
    public List<String> members = new ArrayList<>();

    @JsonProperty
    public long avatarId;

    public GroupInfo(@JsonProperty("groupId") byte[] groupId, @JsonProperty("name") String name, @JsonProperty("members") Collection<String> members, @JsonProperty("avatarId") long avatarId) {
        this.groupId = groupId;
        this.name = name;
        this.members.addAll(members);
        this.avatarId = avatarId;
    }
}
