package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.groups.GroupIdV2;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Set;
import java.util.stream.Collectors;

public final class GroupInfoV2 extends GroupInfo {

    private final GroupIdV2 groupId;
    private final GroupMasterKey masterKey;

    private boolean blocked;
    private DecryptedGroup group; // stored as a file with hexadecimal groupId as name
    private RecipientResolver recipientResolver;
    private boolean permissionDenied;

    public GroupInfoV2(final GroupIdV2 groupId, final GroupMasterKey masterKey) {
        this.groupId = groupId;
        this.masterKey = masterKey;
    }

    public GroupInfoV2(
            final GroupIdV2 groupId,
            final GroupMasterKey masterKey,
            final boolean blocked,
            final boolean permissionDenied
    ) {
        this.groupId = groupId;
        this.masterKey = masterKey;
        this.blocked = blocked;
        this.permissionDenied = permissionDenied;
    }

    @Override
    public GroupIdV2 getGroupId() {
        return groupId;
    }

    public GroupMasterKey getMasterKey() {
        return masterKey;
    }

    public void setGroup(final DecryptedGroup group, final RecipientResolver recipientResolver) {
        if (group != null) {
            this.permissionDenied = false;
        }
        this.group = group;
        this.recipientResolver = recipientResolver;
    }

    public DecryptedGroup getGroup() {
        return group;
    }

    @Override
    public String getTitle() {
        if (this.group == null) {
            return null;
        }
        return this.group.getTitle();
    }

    @Override
    public String getDescription() {
        if (this.group == null) {
            return null;
        }
        return this.group.getDescription();
    }

    @Override
    public GroupInviteLinkUrl getGroupInviteLink() {
        if (this.group == null || this.group.getInviteLinkPassword().isEmpty() || (
                this.group.getAccessControl().getAddFromInviteLink() != AccessControl.AccessRequired.ANY
                        && this.group.getAccessControl().getAddFromInviteLink()
                        != AccessControl.AccessRequired.ADMINISTRATOR
        )) {
            return null;
        }

        return GroupInviteLinkUrl.forGroup(masterKey, group);
    }

    @Override
    public Set<RecipientId> getMembers() {
        if (this.group == null) {
            return Set.of();
        }
        return group.getMembersList()
                .stream()
                .map(m -> UuidUtil.parseOrThrow(m.getUuid().toByteArray()))
                .map(recipientResolver::resolveRecipient)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<RecipientId> getPendingMembers() {
        if (this.group == null) {
            return Set.of();
        }
        return group.getPendingMembersList()
                .stream()
                .map(m -> UuidUtil.parseOrThrow(m.getUuid().toByteArray()))
                .map(recipientResolver::resolveRecipient)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<RecipientId> getRequestingMembers() {
        if (this.group == null) {
            return Set.of();
        }
        return group.getRequestingMembersList()
                .stream()
                .map(m -> UuidUtil.parseOrThrow(m.getUuid().toByteArray()))
                .map(recipientResolver::resolveRecipient)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<RecipientId> getAdminMembers() {
        if (this.group == null) {
            return Set.of();
        }
        return group.getMembersList()
                .stream()
                .filter(m -> m.getRole() == Member.Role.ADMINISTRATOR)
                .map(m -> UuidUtil.parseOrThrow(m.getUuid().toByteArray()))
                .map(recipientResolver::resolveRecipient)
                .collect(Collectors.toSet());
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
    public int getMessageExpirationTimer() {
        return this.group != null && this.group.hasDisappearingMessagesTimer()
                ? this.group.getDisappearingMessagesTimer().getDuration()
                : 0;
    }

    @Override
    public boolean isAnnouncementGroup() {
        return this.group != null && this.group.getIsAnnouncementGroup() == EnabledState.ENABLED;
    }

    @Override
    public GroupPermission getPermissionAddMember() {
        final var accessControl = getAccessControl();
        return accessControl == null ? GroupPermission.EVERY_MEMBER : toGroupPermission(accessControl.getMembers());
    }

    @Override
    public GroupPermission getPermissionEditDetails() {
        final var accessControl = getAccessControl();
        return accessControl == null ? GroupPermission.EVERY_MEMBER : toGroupPermission(accessControl.getAttributes());
    }

    @Override
    public GroupPermission getPermissionSendMessage() {
        return isAnnouncementGroup() ? GroupPermission.ONLY_ADMINS : GroupPermission.EVERY_MEMBER;
    }

    public void setPermissionDenied(final boolean permissionDenied) {
        this.permissionDenied = permissionDenied;
    }

    public boolean isPermissionDenied() {
        return permissionDenied;
    }

    private AccessControl getAccessControl() {
        if (this.group == null || !this.group.hasAccessControl()) {
            return null;
        }

        return this.group.getAccessControl();
    }

    private static GroupPermission toGroupPermission(final AccessControl.AccessRequired permission) {
        return switch (permission) {
            case ADMINISTRATOR -> GroupPermission.ONLY_ADMINS;
            default -> GroupPermission.EVERY_MEMBER;
        };
    }
}
