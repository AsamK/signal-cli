package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupInviteLinkUrl;
import org.asamk.signal.manager.api.GroupPermission;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public sealed abstract class GroupInfo permits GroupInfoV1, GroupInfoV2 {

    public abstract GroupId getGroupId();

    public abstract DistributionId getDistributionId();

    public abstract String getTitle();

    public String getDescription() {
        return null;
    }

    public abstract GroupInviteLinkUrl getGroupInviteLink();

    public abstract Collection<GroupMemberInfo> getMembers();

    public Set<RecipientId> getMemberRecipientIds() {
        return getMembers().stream().map(GroupMemberInfo::getRecipientId).collect(Collectors.toSet());
    }

    public GroupMemberInfo getMember(RecipientId recipientId) {
        return getMembers().stream()
                .filter(member -> member.getRecipientId().equals(recipientId))
                .findFirst()
                .orElseThrow();
    }

    public Set<RecipientId> getBannedMembers() {
        return Set.of();
    }

    public Set<RecipientId> getPendingMembers() {
        return Set.of();
    }

    public Set<RecipientId> getRequestingMembers() {
        return Set.of();
    }

    public Set<RecipientId> getAdminMemberRecipientIds() {
        return Set.of();
    }

    public abstract boolean isBlocked();

    public abstract void setBlocked(boolean blocked);

    public abstract boolean isProfileSharingEnabled();

    public abstract void setProfileSharingEnabled(boolean profileSharingEnabled);

    public abstract int getMessageExpirationTimer();

    public abstract boolean isAnnouncementGroup();

    public abstract GroupPermission getPermissionAddMember();

    public abstract GroupPermission getPermissionEditDetails();

    public abstract GroupPermission getPermissionSendMessage();

    public Set<RecipientId> getMembersWithout(RecipientId recipientId) {
        return getMemberRecipientIds().stream()
                .filter(member -> !member.equals(recipientId))
                .collect(Collectors.toSet());
    }

    public Set<RecipientId> getMembersIncludingPendingWithout(RecipientId recipientId) {
        return Stream.concat(getMemberRecipientIds().stream(), getPendingMembers().stream())
                .filter(member -> !member.equals(recipientId))
                .collect(Collectors.toSet());
    }

    public boolean isMember(RecipientId recipientId) {
        return getMembers().stream().anyMatch(m -> m.getRecipientId().equals(recipientId));
    }

    public boolean isAdmin(RecipientId recipientId) {
        return getMembers().stream().anyMatch(m -> m.isAdmin() && m.getRecipientId().equals(recipientId));
    }

    public boolean isPendingMember(RecipientId recipientId) {
        return getPendingMembers().contains(recipientId);
    }
}
