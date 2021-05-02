package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.groups.GroupIdV1;
import org.asamk.signal.manager.groups.GroupIdV2;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.recipients.RecipientId;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class GroupInfoV1 extends GroupInfo {

    private final GroupIdV1 groupId;

    private GroupIdV2 expectedV2Id;

    public String name;

    public Set<RecipientId> members = new HashSet<>();
    public String color;
    public int messageExpirationTime;
    public boolean blocked;
    public boolean archived;

    public GroupInfoV1(GroupIdV1 groupId) {
        this.groupId = groupId;
    }

    public GroupInfoV1(
            final GroupIdV1 groupId,
            final GroupIdV2 expectedV2Id,
            final String name,
            final Set<RecipientId> members,
            final String color,
            final int messageExpirationTime,
            final boolean blocked,
            final boolean archived
    ) {
        this.groupId = groupId;
        this.expectedV2Id = expectedV2Id;
        this.name = name;
        this.members = members;
        this.color = color;
        this.messageExpirationTime = messageExpirationTime;
        this.blocked = blocked;
        this.archived = archived;
    }

    @Override
    public GroupIdV1 getGroupId() {
        return groupId;
    }

    public GroupIdV2 getExpectedV2Id() {
        if (expectedV2Id == null) {
            expectedV2Id = GroupUtils.getGroupIdV2(groupId);
        }
        return expectedV2Id;
    }

    @Override
    public String getTitle() {
        return name;
    }

    @Override
    public GroupInviteLinkUrl getGroupInviteLink() {
        return null;
    }

    public Set<RecipientId> getMembers() {
        return members;
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
        return messageExpirationTime;
    }

    public void addMembers(Collection<RecipientId> members) {
        this.members.addAll(members);
    }

    public void removeMember(RecipientId recipientId) {
        this.members.removeIf(member -> member.equals(recipientId));
    }
}
