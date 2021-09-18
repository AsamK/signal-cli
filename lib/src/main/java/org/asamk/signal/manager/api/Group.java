package org.asamk.signal.manager.api;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;

import java.util.Set;

public class Group {

    private final GroupId groupId;
    private final String title;
    private final String description;
    private final GroupInviteLinkUrl groupInviteLinkUrl;
    private final Set<RecipientAddress> members;
    private final Set<RecipientAddress> pendingMembers;
    private final Set<RecipientAddress> requestingMembers;
    private final Set<RecipientAddress> adminMembers;
    private final boolean isBlocked;
    private final int messageExpirationTime;
    private final boolean isAnnouncementGroup;
    private final boolean isMember;

    public Group(
            final GroupId groupId,
            final String title,
            final String description,
            final GroupInviteLinkUrl groupInviteLinkUrl,
            final Set<RecipientAddress> members,
            final Set<RecipientAddress> pendingMembers,
            final Set<RecipientAddress> requestingMembers,
            final Set<RecipientAddress> adminMembers,
            final boolean isBlocked,
            final int messageExpirationTime,
            final boolean isAnnouncementGroup,
            final boolean isMember
    ) {
        this.groupId = groupId;
        this.title = title;
        this.description = description;
        this.groupInviteLinkUrl = groupInviteLinkUrl;
        this.members = members;
        this.pendingMembers = pendingMembers;
        this.requestingMembers = requestingMembers;
        this.adminMembers = adminMembers;
        this.isBlocked = isBlocked;
        this.messageExpirationTime = messageExpirationTime;
        this.isAnnouncementGroup = isAnnouncementGroup;
        this.isMember = isMember;
    }

    public GroupId getGroupId() {
        return groupId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public GroupInviteLinkUrl getGroupInviteLinkUrl() {
        return groupInviteLinkUrl;
    }

    public Set<RecipientAddress> getMembers() {
        return members;
    }

    public Set<RecipientAddress> getPendingMembers() {
        return pendingMembers;
    }

    public Set<RecipientAddress> getRequestingMembers() {
        return requestingMembers;
    }

    public Set<RecipientAddress> getAdminMembers() {
        return adminMembers;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public int getMessageExpirationTime() {
        return messageExpirationTime;
    }

    public boolean isAnnouncementGroup() {
        return isAnnouncementGroup;
    }

    public boolean isMember() {
        return isMember;
    }
}
