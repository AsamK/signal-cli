package org.asamk.signal.manager.api;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.recipients.RecipientId;

import java.util.Set;
import java.util.stream.Collectors;

public record Group(
        GroupId groupId,
        String title,
        String description,
        GroupInviteLinkUrl groupInviteLinkUrl,
        Set<RecipientAddress> members,
        Set<RecipientAddress> pendingMembers,
        Set<RecipientAddress> requestingMembers,
        Set<RecipientAddress> adminMembers,
        Set<RecipientAddress> bannedMembers,
        boolean isBlocked,
        int messageExpirationTimer,
        GroupPermission permissionAddMember,
        GroupPermission permissionEditDetails,
        GroupPermission permissionSendMessage,
        boolean isMember,
        boolean isAdmin
) {

    public static Group from(
            final GroupInfo groupInfo, final RecipientAddressResolver recipientStore, final RecipientId selfRecipientId
    ) {
        return new Group(groupInfo.getGroupId(),
                groupInfo.getTitle(),
                groupInfo.getDescription(),
                groupInfo.getGroupInviteLink(),
                groupInfo.getMembers()
                        .stream()
                        .map(recipientStore::resolveRecipientAddress)
                        .map(org.asamk.signal.manager.storage.recipients.RecipientAddress::toApiRecipientAddress)
                        .collect(Collectors.toSet()),
                groupInfo.getPendingMembers()
                        .stream()
                        .map(recipientStore::resolveRecipientAddress)
                        .map(org.asamk.signal.manager.storage.recipients.RecipientAddress::toApiRecipientAddress)
                        .collect(Collectors.toSet()),
                groupInfo.getRequestingMembers()
                        .stream()
                        .map(recipientStore::resolveRecipientAddress)
                        .map(org.asamk.signal.manager.storage.recipients.RecipientAddress::toApiRecipientAddress)
                        .collect(Collectors.toSet()),
                groupInfo.getAdminMembers()
                        .stream()
                        .map(recipientStore::resolveRecipientAddress)
                        .map(org.asamk.signal.manager.storage.recipients.RecipientAddress::toApiRecipientAddress)
                        .collect(Collectors.toSet()),
                groupInfo.getBannedMembers()
                        .stream()
                        .map(recipientStore::resolveRecipientAddress)
                        .map(org.asamk.signal.manager.storage.recipients.RecipientAddress::toApiRecipientAddress)
                        .collect(Collectors.toSet()),
                groupInfo.isBlocked(),
                groupInfo.getMessageExpirationTimer(),
                groupInfo.getPermissionAddMember(),
                groupInfo.getPermissionEditDetails(),
                groupInfo.getPermissionSendMessage(),
                groupInfo.isMember(selfRecipientId),
                groupInfo.isAdmin(selfRecipientId));
    }
}
