package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.groups.GroupIdV2;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Set;
import java.util.stream.Collectors;

public class GroupInfoV2 extends GroupInfo {

    private final GroupIdV2 groupId;
    private final GroupMasterKey masterKey;

    private boolean blocked;
    private DecryptedGroup group; // stored as a file with hexadecimal groupId as name
    private RecipientResolver recipientResolver;

    public GroupInfoV2(final GroupIdV2 groupId, final GroupMasterKey masterKey) {
        this.groupId = groupId;
        this.masterKey = masterKey;
    }

    public GroupInfoV2(final GroupIdV2 groupId, final GroupMasterKey masterKey, final boolean blocked) {
        this.groupId = groupId;
        this.masterKey = masterKey;
        this.blocked = blocked;
    }

    @Override
    public GroupIdV2 getGroupId() {
        return groupId;
    }

    public GroupMasterKey getMasterKey() {
        return masterKey;
    }

    public void setGroup(final DecryptedGroup group, final RecipientResolver recipientResolver) {
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
                .map(m -> new SignalServiceAddress(UuidUtil.parseOrThrow(m.getUuid().toByteArray()), null))
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
                .map(m -> new SignalServiceAddress(UuidUtil.parseOrThrow(m.getUuid().toByteArray()), null))
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
                .map(m -> new SignalServiceAddress(UuidUtil.parseOrThrow(m.getUuid().toByteArray()), null))
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
                .map(m -> new SignalServiceAddress(UuidUtil.parseOrThrow(m.getUuid().toByteArray()), null))
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
    public int getMessageExpirationTime() {
        return this.group != null && this.group.hasDisappearingMessagesTimer()
                ? this.group.getDisappearingMessagesTimer().getDuration()
                : 0;
    }
}
