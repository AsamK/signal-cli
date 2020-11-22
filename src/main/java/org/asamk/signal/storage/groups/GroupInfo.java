package org.asamk.signal.storage.groups;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.HashSet;
import java.util.Set;

public abstract class GroupInfo {

    @JsonProperty
    public final byte[] groupId;

    public GroupInfo(byte[] groupId) {
        this.groupId = groupId;
    }

    @JsonIgnore
    public abstract String getTitle();

    @JsonIgnore
    public abstract Set<SignalServiceAddress> getMembers();

    @JsonIgnore
    public abstract boolean isBlocked();

    @JsonIgnore
    public abstract void setBlocked(boolean blocked);

    @JsonIgnore
    public abstract int getMessageExpirationTime();

    @JsonIgnore
    public Set<SignalServiceAddress> getMembersWithout(SignalServiceAddress address) {
        Set<SignalServiceAddress> members = new HashSet<>();
        for (SignalServiceAddress member : getMembers()) {
            if (!member.matches(address)) {
                members.add(member);
            }
        }
        return members;
    }

    @JsonIgnore
    public boolean isMember(SignalServiceAddress address) {
        for (SignalServiceAddress member : getMembers()) {
            if (member.matches(address)) {
                return true;
            }
        }
        return false;
    }
}
