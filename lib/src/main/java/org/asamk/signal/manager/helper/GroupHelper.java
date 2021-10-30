package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.AvatarStore;
import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdV1;
import org.asamk.signal.manager.groups.GroupIdV2;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.util.AttachmentUtils;
import org.asamk.signal.manager.util.IOUtils;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.exceptions.ConflictException;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GroupHelper {

    private final static Logger logger = LoggerFactory.getLogger(GroupHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final AttachmentHelper attachmentHelper;
    private final SendHelper sendHelper;
    private final GroupV2Helper groupV2Helper;
    private final AvatarStore avatarStore;
    private final SignalServiceAddressResolver addressResolver;
    private final RecipientResolver recipientResolver;

    public GroupHelper(
            final SignalAccount account,
            final SignalDependencies dependencies,
            final AttachmentHelper attachmentHelper,
            final SendHelper sendHelper,
            final GroupV2Helper groupV2Helper,
            final AvatarStore avatarStore,
            final SignalServiceAddressResolver addressResolver,
            final RecipientResolver recipientResolver
    ) {
        this.account = account;
        this.dependencies = dependencies;
        this.attachmentHelper = attachmentHelper;
        this.sendHelper = sendHelper;
        this.groupV2Helper = groupV2Helper;
        this.avatarStore = avatarStore;
        this.addressResolver = addressResolver;
        this.recipientResolver = recipientResolver;
    }

    public GroupInfo getGroup(GroupId groupId) {
        return getGroup(groupId, false);
    }

    public boolean isGroupBlocked(final GroupId groupId) {
        var group = getGroup(groupId);
        return group != null && group.isBlocked();
    }

    public void downloadGroupAvatar(GroupIdV1 groupId, SignalServiceAttachment avatar) {
        try {
            avatarStore.storeGroupAvatar(groupId,
                    outputStream -> attachmentHelper.retrieveAttachment(avatar, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download avatar for group {}, ignoring: {}", groupId.toBase64(), e.getMessage());
        }
    }

    public Optional<SignalServiceAttachmentStream> createGroupAvatarAttachment(GroupIdV1 groupId) throws IOException {
        final var streamDetails = avatarStore.retrieveGroupAvatar(groupId);
        if (streamDetails == null) {
            return Optional.absent();
        }

        return Optional.of(AttachmentUtils.createAttachment(streamDetails, Optional.absent()));
    }

    public GroupInfoV2 getOrMigrateGroup(
            final GroupMasterKey groupMasterKey, final int revision, final byte[] signedGroupChange
    ) {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

        var groupId = GroupUtils.getGroupIdV2(groupSecretParams);
        var groupInfo = getGroup(groupId);
        final GroupInfoV2 groupInfoV2;
        if (groupInfo instanceof GroupInfoV1) {
            // Received a v2 group message for a v1 group, we need to locally migrate the group
            account.getGroupStore().deleteGroupV1(((GroupInfoV1) groupInfo).getGroupId());
            groupInfoV2 = new GroupInfoV2(groupId, groupMasterKey);
            logger.info("Locally migrated group {} to group v2, id: {}",
                    groupInfo.getGroupId().toBase64(),
                    groupInfoV2.getGroupId().toBase64());
        } else if (groupInfo instanceof GroupInfoV2) {
            groupInfoV2 = (GroupInfoV2) groupInfo;
        } else {
            groupInfoV2 = new GroupInfoV2(groupId, groupMasterKey);
        }

        if (groupInfoV2.getGroup() == null || groupInfoV2.getGroup().getRevision() < revision) {
            DecryptedGroup group = null;
            if (signedGroupChange != null
                    && groupInfoV2.getGroup() != null
                    && groupInfoV2.getGroup().getRevision() + 1 == revision) {
                group = groupV2Helper.getUpdatedDecryptedGroup(groupInfoV2.getGroup(),
                        signedGroupChange,
                        groupMasterKey);
            }
            if (group == null) {
                try {
                    group = groupV2Helper.getDecryptedGroup(groupSecretParams);
                } catch (NotAGroupMemberException ignored) {
                }
            }
            if (group != null) {
                storeProfileKeysFromMembers(group);
                final var avatar = group.getAvatar();
                if (avatar != null && !avatar.isEmpty()) {
                    downloadGroupAvatar(groupId, groupSecretParams, avatar);
                }
            }
            groupInfoV2.setGroup(group, recipientResolver);
            account.getGroupStore().updateGroup(groupInfoV2);
        }

        return groupInfoV2;
    }

    public Pair<GroupId, SendGroupMessageResults> createGroup(
            String name, Set<RecipientId> members, File avatarFile
    ) throws IOException, AttachmentInvalidException {
        final var selfRecipientId = account.getSelfRecipientId();
        if (members != null && members.contains(selfRecipientId)) {
            members = new HashSet<>(members);
            members.remove(selfRecipientId);
        }

        var gv2Pair = groupV2Helper.createGroup(name == null ? "" : name,
                members == null ? Set.of() : members,
                avatarFile);

        if (gv2Pair == null) {
            // Failed to create v2 group, creating v1 group instead
            var gv1 = new GroupInfoV1(GroupIdV1.createRandom());
            gv1.addMembers(List.of(selfRecipientId));
            final var result = updateGroupV1(gv1, name, members, avatarFile);
            return new Pair<>(gv1.getGroupId(), result);
        }

        final var gv2 = gv2Pair.first();
        final var decryptedGroup = gv2Pair.second();

        gv2.setGroup(decryptedGroup, recipientResolver);
        if (avatarFile != null) {
            avatarStore.storeGroupAvatar(gv2.getGroupId(),
                    outputStream -> IOUtils.copyFileToStream(avatarFile, outputStream));
        }

        account.getGroupStore().updateGroup(gv2);

        final var messageBuilder = getGroupUpdateMessageBuilder(gv2, null);

        final var result = sendGroupMessage(messageBuilder, gv2.getMembersIncludingPendingWithout(selfRecipientId));
        return new Pair<>(gv2.getGroupId(), result);
    }

    public SendGroupMessageResults updateGroup(
            final GroupId groupId,
            final String name,
            final String description,
            final Set<RecipientId> members,
            final Set<RecipientId> removeMembers,
            final Set<RecipientId> admins,
            final Set<RecipientId> removeAdmins,
            final boolean resetGroupLink,
            final GroupLinkState groupLinkState,
            final GroupPermission addMemberPermission,
            final GroupPermission editDetailsPermission,
            final File avatarFile,
            final Integer expirationTimer,
            final Boolean isAnnouncementGroup
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, GroupSendingNotAllowedException {
        var group = getGroupForUpdating(groupId);

        if (group instanceof GroupInfoV2) {
            try {
                return updateGroupV2((GroupInfoV2) group,
                        name,
                        description,
                        members,
                        removeMembers,
                        admins,
                        removeAdmins,
                        resetGroupLink,
                        groupLinkState,
                        addMemberPermission,
                        editDetailsPermission,
                        avatarFile,
                        expirationTimer,
                        isAnnouncementGroup);
            } catch (ConflictException e) {
                // Detected conflicting update, refreshing group and trying again
                group = getGroup(groupId, true);
                return updateGroupV2((GroupInfoV2) group,
                        name,
                        description,
                        members,
                        removeMembers,
                        admins,
                        removeAdmins,
                        resetGroupLink,
                        groupLinkState,
                        addMemberPermission,
                        editDetailsPermission,
                        avatarFile,
                        expirationTimer,
                        isAnnouncementGroup);
            }
        }

        final var gv1 = (GroupInfoV1) group;
        final var result = updateGroupV1(gv1, name, members, avatarFile);
        if (expirationTimer != null) {
            setExpirationTimer(gv1, expirationTimer);
        }
        return result;
    }

    public Pair<GroupId, SendGroupMessageResults> joinGroup(
            GroupInviteLinkUrl inviteLinkUrl
    ) throws IOException, GroupLinkNotActiveException {
        final var groupJoinInfo = groupV2Helper.getDecryptedGroupJoinInfo(inviteLinkUrl.getGroupMasterKey(),
                inviteLinkUrl.getPassword());
        final var groupChange = groupV2Helper.joinGroup(inviteLinkUrl.getGroupMasterKey(),
                inviteLinkUrl.getPassword(),
                groupJoinInfo);
        final var group = getOrMigrateGroup(inviteLinkUrl.getGroupMasterKey(),
                groupJoinInfo.getRevision() + 1,
                groupChange.toByteArray());

        if (group.getGroup() == null) {
            // Only requested member, can't send update to group members
            return new Pair<>(group.getGroupId(), new SendGroupMessageResults(0, List.of()));
        }

        final var result = sendUpdateGroupV2Message(group, group.getGroup(), groupChange);

        return new Pair<>(group.getGroupId(), result);
    }

    public SendGroupMessageResults quitGroup(
            final GroupId groupId, final Set<RecipientId> newAdmins
    ) throws IOException, LastGroupAdminException, NotAGroupMemberException, GroupNotFoundException {
        var group = getGroupForUpdating(groupId);
        if (group instanceof GroupInfoV1) {
            return quitGroupV1((GroupInfoV1) group);
        }

        try {
            return quitGroupV2((GroupInfoV2) group, newAdmins);
        } catch (ConflictException e) {
            // Detected conflicting update, refreshing group and trying again
            group = getGroup(groupId, true);
            return quitGroupV2((GroupInfoV2) group, newAdmins);
        }
    }

    public void deleteGroup(GroupId groupId) throws IOException {
        account.getGroupStore().deleteGroup(groupId);
        avatarStore.deleteGroupAvatar(groupId);
    }

    public void setGroupBlocked(final GroupId groupId, final boolean blocked) throws GroupNotFoundException {
        var group = getGroup(groupId);
        if (group == null) {
            throw new GroupNotFoundException(groupId);
        }

        group.setBlocked(blocked);
        account.getGroupStore().updateGroup(group);
    }

    public SendGroupMessageResults sendGroupInfoRequest(
            GroupIdV1 groupId, RecipientId recipientId
    ) throws IOException {
        var group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.REQUEST_INFO).withId(groupId.serialize());

        var messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group.build());

        // Send group info request message to the recipient who sent us a message with this groupId
        return sendGroupMessage(messageBuilder, Set.of(recipientId));
    }

    public SendGroupMessageResults sendGroupInfoMessage(
            GroupIdV1 groupId, RecipientId recipientId
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, AttachmentInvalidException {
        GroupInfoV1 g;
        var group = getGroupForUpdating(groupId);
        if (!(group instanceof GroupInfoV1)) {
            throw new IOException("Received an invalid group request for a v2 group!");
        }
        g = (GroupInfoV1) group;

        if (!g.isMember(recipientId)) {
            throw new NotAGroupMemberException(groupId, g.name);
        }

        var messageBuilder = getGroupUpdateMessageBuilder(g);

        // Send group message only to the recipient who requested it
        return sendGroupMessage(messageBuilder, Set.of(recipientId));
    }

    private GroupInfo getGroup(GroupId groupId, boolean forceUpdate) {
        final var group = account.getGroupStore().getGroup(groupId);
        if (group instanceof GroupInfoV2 groupInfoV2) {
            if (forceUpdate || (!groupInfoV2.isPermissionDenied() && groupInfoV2.getGroup() == null)) {
                final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
                DecryptedGroup decryptedGroup;
                try {
                    decryptedGroup = groupV2Helper.getDecryptedGroup(groupSecretParams);
                } catch (NotAGroupMemberException e) {
                    groupInfoV2.setPermissionDenied(true);
                    decryptedGroup = null;
                }
                groupInfoV2.setGroup(decryptedGroup, recipientResolver);
                account.getGroupStore().updateGroup(group);
            }
        }
        return group;
    }

    private void downloadGroupAvatar(GroupIdV2 groupId, GroupSecretParams groupSecretParams, String cdnKey) {
        try {
            avatarStore.storeGroupAvatar(groupId,
                    outputStream -> retrieveGroupV2Avatar(groupSecretParams, cdnKey, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download avatar for group {}, ignoring: {}", groupId.toBase64(), e.getMessage());
        }
    }

    private void retrieveGroupV2Avatar(
            GroupSecretParams groupSecretParams, String cdnKey, OutputStream outputStream
    ) throws IOException {
        var groupOperations = dependencies.getGroupsV2Operations().forGroup(groupSecretParams);

        var tmpFile = IOUtils.createTempFile();
        try (InputStream input = dependencies.getMessageReceiver()
                .retrieveGroupsV2ProfileAvatar(cdnKey, tmpFile, ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE)) {
            var encryptedData = IOUtils.readFully(input);

            var decryptedData = groupOperations.decryptAvatar(encryptedData);
            outputStream.write(decryptedData);
        } finally {
            try {
                Files.delete(tmpFile.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete received group avatar temp file “{}”, ignoring: {}",
                        tmpFile,
                        e.getMessage());
            }
        }
    }

    private void storeProfileKeysFromMembers(final DecryptedGroup group) {
        for (var member : group.getMembersList()) {
            final var uuid = UuidUtil.parseOrThrow(member.getUuid().toByteArray());
            final var recipientId = account.getRecipientStore().resolveRecipient(uuid);
            try {
                account.getProfileStore()
                        .storeProfileKey(recipientId, new ProfileKey(member.getProfileKey().toByteArray()));
            } catch (InvalidInputException ignored) {
            }
        }
    }

    private GroupInfo getGroupForUpdating(GroupId groupId) throws GroupNotFoundException, NotAGroupMemberException {
        var g = getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        if (!g.isMember(account.getSelfRecipientId()) && !g.isPendingMember(account.getSelfRecipientId())) {
            throw new NotAGroupMemberException(groupId, g.getTitle());
        }
        return g;
    }

    private SendGroupMessageResults updateGroupV1(
            final GroupInfoV1 gv1, final String name, final Set<RecipientId> members, final File avatarFile
    ) throws IOException, AttachmentInvalidException {
        updateGroupV1Details(gv1, name, members, avatarFile);

        account.getGroupStore().updateGroup(gv1);

        var messageBuilder = getGroupUpdateMessageBuilder(gv1);
        return sendGroupMessage(messageBuilder, gv1.getMembersIncludingPendingWithout(account.getSelfRecipientId()));
    }

    private void updateGroupV1Details(
            final GroupInfoV1 g, final String name, final Collection<RecipientId> members, final File avatarFile
    ) throws IOException {
        if (name != null) {
            g.name = name;
        }

        if (members != null) {
            g.addMembers(members);
        }

        if (avatarFile != null) {
            avatarStore.storeGroupAvatar(g.getGroupId(),
                    outputStream -> IOUtils.copyFileToStream(avatarFile, outputStream));
        }
    }

    /**
     * Change the expiration timer for a group
     */
    private void setExpirationTimer(
            GroupInfoV1 groupInfoV1, int messageExpirationTimer
    ) throws NotAGroupMemberException, GroupNotFoundException, IOException, GroupSendingNotAllowedException {
        groupInfoV1.messageExpirationTime = messageExpirationTimer;
        account.getGroupStore().updateGroup(groupInfoV1);
        sendExpirationTimerUpdate(groupInfoV1.getGroupId());
    }

    private void sendExpirationTimerUpdate(GroupIdV1 groupId) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        final var messageBuilder = SignalServiceDataMessage.newBuilder().asExpirationUpdate();
        sendHelper.sendAsGroupMessage(messageBuilder, groupId);
    }

    private SendGroupMessageResults updateGroupV2(
            final GroupInfoV2 group,
            final String name,
            final String description,
            final Set<RecipientId> members,
            final Set<RecipientId> removeMembers,
            final Set<RecipientId> admins,
            final Set<RecipientId> removeAdmins,
            final boolean resetGroupLink,
            final GroupLinkState groupLinkState,
            final GroupPermission addMemberPermission,
            final GroupPermission editDetailsPermission,
            final File avatarFile,
            final Integer expirationTimer,
            final Boolean isAnnouncementGroup
    ) throws IOException {
        SendGroupMessageResults result = null;
        if (group.isPendingMember(account.getSelfRecipientId())) {
            var groupGroupChangePair = groupV2Helper.acceptInvite(group);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (members != null) {
            final var newMembers = new HashSet<>(members);
            newMembers.removeAll(group.getMembers());
            if (newMembers.size() > 0) {
                var groupGroupChangePair = groupV2Helper.addMembers(group, newMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }
        }

        if (removeMembers != null) {
            var existingRemoveMembers = new HashSet<>(removeMembers);
            existingRemoveMembers.retainAll(group.getMembers());
            existingRemoveMembers.remove(account.getSelfRecipientId());// self can be removed with sendQuitGroupMessage
            if (existingRemoveMembers.size() > 0) {
                var groupGroupChangePair = groupV2Helper.removeMembers(group, existingRemoveMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }

            var pendingRemoveMembers = new HashSet<>(removeMembers);
            pendingRemoveMembers.retainAll(group.getPendingMembers());
            if (pendingRemoveMembers.size() > 0) {
                var groupGroupChangePair = groupV2Helper.revokeInvitedMembers(group, pendingRemoveMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }
        }

        if (admins != null) {
            final var newAdmins = new HashSet<>(admins);
            newAdmins.retainAll(group.getMembers());
            newAdmins.removeAll(group.getAdminMembers());
            if (newAdmins.size() > 0) {
                for (var admin : newAdmins) {
                    var groupGroupChangePair = groupV2Helper.setMemberAdmin(group, admin, true);
                    result = sendUpdateGroupV2Message(group,
                            groupGroupChangePair.first(),
                            groupGroupChangePair.second());
                }
            }
        }

        if (removeAdmins != null) {
            final var existingRemoveAdmins = new HashSet<>(removeAdmins);
            existingRemoveAdmins.retainAll(group.getAdminMembers());
            if (existingRemoveAdmins.size() > 0) {
                for (var admin : existingRemoveAdmins) {
                    var groupGroupChangePair = groupV2Helper.setMemberAdmin(group, admin, false);
                    result = sendUpdateGroupV2Message(group,
                            groupGroupChangePair.first(),
                            groupGroupChangePair.second());
                }
            }
        }

        if (resetGroupLink) {
            var groupGroupChangePair = groupV2Helper.resetGroupLinkPassword(group);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (groupLinkState != null) {
            var groupGroupChangePair = groupV2Helper.setGroupLinkState(group, groupLinkState);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (addMemberPermission != null) {
            var groupGroupChangePair = groupV2Helper.setAddMemberPermission(group, addMemberPermission);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (editDetailsPermission != null) {
            var groupGroupChangePair = groupV2Helper.setEditDetailsPermission(group, editDetailsPermission);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (expirationTimer != null) {
            var groupGroupChangePair = groupV2Helper.setMessageExpirationTimer(group, expirationTimer);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (isAnnouncementGroup != null) {
            var groupGroupChangePair = groupV2Helper.setIsAnnouncementGroup(group, isAnnouncementGroup);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (name != null || description != null || avatarFile != null) {
            var groupGroupChangePair = groupV2Helper.updateGroup(group, name, description, avatarFile);
            if (avatarFile != null) {
                avatarStore.storeGroupAvatar(group.getGroupId(),
                        outputStream -> IOUtils.copyFileToStream(avatarFile, outputStream));
            }
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        return result;
    }

    private SendGroupMessageResults quitGroupV1(final GroupInfoV1 groupInfoV1) throws IOException {
        var group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.QUIT)
                .withId(groupInfoV1.getGroupId().serialize())
                .build();

        var messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group);
        groupInfoV1.removeMember(account.getSelfRecipientId());
        account.getGroupStore().updateGroup(groupInfoV1);
        return sendGroupMessage(messageBuilder,
                groupInfoV1.getMembersIncludingPendingWithout(account.getSelfRecipientId()));
    }

    private SendGroupMessageResults quitGroupV2(
            final GroupInfoV2 groupInfoV2, final Set<RecipientId> newAdmins
    ) throws LastGroupAdminException, IOException {
        final var currentAdmins = groupInfoV2.getAdminMembers();
        newAdmins.removeAll(currentAdmins);
        newAdmins.retainAll(groupInfoV2.getMembers());
        if (currentAdmins.contains(account.getSelfRecipientId())
                && currentAdmins.size() == 1
                && groupInfoV2.getMembers().size() > 1
                && newAdmins.size() == 0) {
            // Last admin can't leave the group, unless she's also the last member
            throw new LastGroupAdminException(groupInfoV2.getGroupId(), groupInfoV2.getTitle());
        }
        final var groupGroupChangePair = groupV2Helper.leaveGroup(groupInfoV2, newAdmins);
        groupInfoV2.setGroup(groupGroupChangePair.first(), recipientResolver);
        account.getGroupStore().updateGroup(groupInfoV2);

        var messageBuilder = getGroupUpdateMessageBuilder(groupInfoV2, groupGroupChangePair.second().toByteArray());
        return sendGroupMessage(messageBuilder,
                groupInfoV2.getMembersIncludingPendingWithout(account.getSelfRecipientId()));
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfoV1 g) throws AttachmentInvalidException {
        var group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                .withId(g.getGroupId().serialize())
                .withName(g.name)
                .withMembers(g.getMembers()
                        .stream()
                        .map(addressResolver::resolveSignalServiceAddress)
                        .collect(Collectors.toList()));

        try {
            final var attachment = createGroupAvatarAttachment(g.getGroupId());
            if (attachment.isPresent()) {
                group.withAvatar(attachment.get());
            }
        } catch (IOException e) {
            throw new AttachmentInvalidException(g.getGroupId().toBase64(), e);
        }

        return SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build())
                .withExpiration(g.getMessageExpirationTimer());
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfoV2 g, byte[] signedGroupChange) {
        var group = SignalServiceGroupV2.newBuilder(g.getMasterKey())
                .withRevision(g.getGroup().getRevision())
                .withSignedGroupChange(signedGroupChange);
        return SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build())
                .withExpiration(g.getMessageExpirationTimer());
    }

    private SendGroupMessageResults sendUpdateGroupV2Message(
            GroupInfoV2 group, DecryptedGroup newDecryptedGroup, GroupChange groupChange
    ) throws IOException {
        final var selfRecipientId = account.getSelfRecipientId();
        final var members = group.getMembersIncludingPendingWithout(selfRecipientId);
        group.setGroup(newDecryptedGroup, recipientResolver);
        members.addAll(group.getMembersIncludingPendingWithout(selfRecipientId));
        account.getGroupStore().updateGroup(group);

        final var messageBuilder = getGroupUpdateMessageBuilder(group, groupChange.toByteArray());
        return sendGroupMessage(messageBuilder, members);
    }

    private SendGroupMessageResults sendGroupMessage(
            final SignalServiceDataMessage.Builder messageBuilder, final Set<RecipientId> members
    ) throws IOException {
        final var timestamp = System.currentTimeMillis();
        messageBuilder.withTimestamp(timestamp);
        final var results = sendHelper.sendGroupMessage(messageBuilder.build(), members);
        return new SendGroupMessageResults(timestamp, results);
    }
}
