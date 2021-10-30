package org.asamk.signal.manager.api;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;

import java.util.Set;

public record Group(
        GroupId groupId,
        String title,
        String description,
        GroupInviteLinkUrl groupInviteLinkUrl,
        Set<RecipientAddress> members,
        Set<RecipientAddress> pendingMembers,
        Set<RecipientAddress> requestingMembers,
        Set<RecipientAddress> adminMembers,
        boolean isBlocked,
        int messageExpirationTimer,
        GroupPermission permissionAddMember,
        GroupPermission permissionEditDetails,
        GroupPermission permissionSendMessage,
        boolean isMember,
        boolean isAdmin
) {}
