package org.asamk.signal.manager.api;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupPermission;
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
    private final int messageExpirationTimer;

    private final GroupPermission permissionAddMember;
    private final GroupPermission permissionEditDetails;
    private final GroupPermission permissionSendMessage;
    private final boolean isMember;
    private final boolean isAdmin;

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
            final int messageExpirationTimer,
            final GroupPermission permissionAddMember,
            final GroupPermission permissionEditDetails,
            final GroupPermission permissionSendMessage,
            final boolean isMember,
            final boolean isAdmin
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
        this.messageExpirationTimer = messageExpirationTimer;
        this.permissionAddMember = permissionAddMember;
        this.permissionEditDetails = permissionEditDetails;
        this.permissionSendMessage = permissionSendMessage;
        this.isMember = isMember;
        this.isAdmin = isAdmin;
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

    public int getMessageExpirationTimer() {
        return messageExpirationTimer;
    }

    public GroupPermission getPermissionAddMember() {
        return permissionAddMember;
    }

    public GroupPermission getPermissionEditDetails() {
        return permissionEditDetails;
    }

    public GroupPermission getPermissionSendMessage() {
        return permissionSendMessage;
    }

    public boolean isMember() {
        return isMember;
    }

    public boolean isAdmin() {
        return isAdmin;
    }
}
