package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.api.GroupIdV2;
import org.asamk.signal.manager.api.GroupInviteLinkUrl;
import org.asamk.signal.manager.api.GroupPermission;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.Set;
import java.util.stream.Collectors;

public final class GroupInfoV2 extends GroupInfo {

    private final GroupIdV2 groupId;
    private final GroupMasterKey masterKey;
    private final DistributionId distributionId;
    private boolean blocked;
    private DecryptedGroup group;
    private boolean permissionDenied;

    private final RecipientResolver recipientResolver;

    public GroupInfoV2(
            final GroupIdV2 groupId, final GroupMasterKey masterKey, final RecipientResolver recipientResolver
    ) {
        this.groupId = groupId;
        this.masterKey = masterKey;
        this.distributionId = DistributionId.create();
        this.recipientResolver = recipientResolver;
    }

    public GroupInfoV2(
            final GroupIdV2 groupId,
            final GroupMasterKey masterKey,
            final DecryptedGroup group,
            final DistributionId distributionId,
            final boolean blocked,
            final boolean permissionDenied,
            final RecipientResolver recipientResolver
    ) {
        this.groupId = groupId;
        this.masterKey = masterKey;
        this.group = group;
        this.distributionId = distributionId;
        this.blocked = blocked;
        this.permissionDenied = permissionDenied;
        this.recipientResolver = recipientResolver;
    }

    @Override
    public GroupIdV2 getGroupId() {
        return groupId;
    }

    public GroupMasterKey getMasterKey() {
        return masterKey;
    }

    public DistributionId getDistributionId() {
        return distributionId;
    }

    public void setGroup(final DecryptedGroup group) {
        if (group != null) {
            this.permissionDenied = false;
        }
        this.group = group;
    }

    public DecryptedGroup getGroup() {
        return group;
    }

    @Override
    public String getTitle() {
        if (this.group == null) {
            return null;
        }
        return this.group.title;
    }

    @Override
    public String getDescription() {
        if (this.group == null) {
            return null;
        }
        return this.group.description;
    }

    @Override
    public GroupInviteLinkUrl getGroupInviteLink() {
        if (this.group == null || this.group.inviteLinkPassword.toByteArray().length == 0 || (
                this.group.accessControl != null
                        && this.group.accessControl.addFromInviteLink != AccessControl.AccessRequired.ANY
                        && this.group.accessControl.addFromInviteLink != AccessControl.AccessRequired.ADMINISTRATOR
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
        return group.members.stream()
                .map(m -> ServiceId.parseOrThrow(m.aciBytes))
                .map(recipientResolver::resolveRecipient)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<RecipientId> getBannedMembers() {
        if (this.group == null) {
            return Set.of();
        }
        return group.bannedMembers.stream()
                .map(m -> ServiceId.parseOrThrow(m.serviceIdBytes))
                .map(recipientResolver::resolveRecipient)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<RecipientId> getPendingMembers() {
        if (this.group == null) {
            return Set.of();
        }
        return group.pendingMembers.stream()
                .map(m -> ServiceId.parseOrThrow(m.serviceIdBytes))
                .map(recipientResolver::resolveRecipient)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<RecipientId> getRequestingMembers() {
        if (this.group == null) {
            return Set.of();
        }
        return group.requestingMembers.stream()
                .map(m -> ServiceId.parseOrThrow(m.aciBytes))
                .map(recipientResolver::resolveRecipient)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<RecipientId> getAdminMembers() {
        if (this.group == null) {
            return Set.of();
        }
        return group.members.stream()
                .filter(m -> m.role == Member.Role.ADMINISTRATOR)
                .map(m -> new RecipientAddress(ServiceId.ACI.parseOrNull(m.aciBytes),
                        ServiceId.PNI.parseOrNull(m.pniBytes),
                        null))
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
        return this.group != null && this.group.disappearingMessagesTimer != null
                ? this.group.disappearingMessagesTimer.duration
                : 0;
    }

    @Override
    public boolean isAnnouncementGroup() {
        return this.group != null && this.group.isAnnouncementGroup == EnabledState.ENABLED;
    }

    @Override
    public GroupPermission getPermissionAddMember() {
        final var accessControl = getAccessControl();
        return accessControl == null ? GroupPermission.EVERY_MEMBER : toGroupPermission(accessControl.members);
    }

    @Override
    public GroupPermission getPermissionEditDetails() {
        final var accessControl = getAccessControl();
        return accessControl == null ? GroupPermission.EVERY_MEMBER : toGroupPermission(accessControl.attributes);
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
        if (this.group == null || this.group.accessControl == null) {
            return null;
        }

        return this.group.accessControl;
    }

    private static GroupPermission toGroupPermission(final AccessControl.AccessRequired permission) {
        return switch (permission) {
            case ADMINISTRATOR -> GroupPermission.ONLY_ADMINS;
            default -> GroupPermission.EVERY_MEMBER;
        };
    }
}
