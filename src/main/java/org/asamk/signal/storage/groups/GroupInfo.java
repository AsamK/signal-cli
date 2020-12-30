package org.asamk.signal.storage.groups;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.asamk.signal.manager.GroupId;
import org.asamk.signal.manager.GroupInviteLinkUrl;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class GroupInfo {

    @JsonIgnore
    public abstract GroupId getGroupId();

    @JsonIgnore
    public abstract String getTitle();

    @JsonIgnore
    public abstract GroupInviteLinkUrl getGroupInviteLink();

    @JsonIgnore
    public abstract Set<SignalServiceAddress> getMembers();

    @JsonIgnore
    public Set<SignalServiceAddress> getPendingMembers() {
        return Set.of();
    }

    @JsonIgnore
    public Set<SignalServiceAddress> getRequestingMembers() {
        return Set.of();
    }

    @JsonIgnore
    public abstract boolean isBlocked();

    @JsonIgnore
    public abstract void setBlocked(boolean blocked);

    @JsonIgnore
    public abstract int getMessageExpirationTime();

    @JsonIgnore
    public Set<SignalServiceAddress> getMembersWithout(SignalServiceAddress address) {
        return getMembers().stream().filter(member -> !member.matches(address)).collect(Collectors.toSet());
    }

    @JsonIgnore
    public Set<SignalServiceAddress> getMembersIncludingPendingWithout(SignalServiceAddress address) {
        return Stream.concat(getMembers().stream(), getPendingMembers().stream())
                .filter(member -> !member.matches(address))
                .collect(Collectors.toSet());
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

    @JsonIgnore
    public boolean isPendingMember(SignalServiceAddress address) {
        for (SignalServiceAddress member : getPendingMembers()) {
            if (member.matches(address)) {
                return true;
            }
        }
        return false;
    }
}
