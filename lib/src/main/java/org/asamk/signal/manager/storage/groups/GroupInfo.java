package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.storage.recipients.RecipientId;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class GroupInfo {

    public abstract GroupId getGroupId();

    public abstract String getTitle();

    public String getDescription() {
        return null;
    }

    public abstract GroupInviteLinkUrl getGroupInviteLink();

    public abstract Set<RecipientId> getMembers();

    public Set<RecipientId> getPendingMembers() {
        return Set.of();
    }

    public Set<RecipientId> getRequestingMembers() {
        return Set.of();
    }

    public Set<RecipientId> getAdminMembers() {
        return Set.of();
    }

    public abstract boolean isBlocked();

    public abstract void setBlocked(boolean blocked);

    public abstract int getMessageExpirationTime();

    public Set<RecipientId> getMembersWithout(RecipientId recipientId) {
        return getMembers().stream().filter(member -> !member.equals(recipientId)).collect(Collectors.toSet());
    }

    public Set<RecipientId> getMembersIncludingPendingWithout(RecipientId recipientId) {
        return Stream.concat(getMembers().stream(), getPendingMembers().stream())
                .filter(member -> !member.equals(recipientId))
                .collect(Collectors.toSet());
    }

    public boolean isMember(RecipientId recipientId) {
        return getMembers().contains(recipientId);
    }

    public boolean isPendingMember(RecipientId recipientId) {
        return getPendingMembers().contains(recipientId);
    }
}
