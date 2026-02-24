package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupIdV1;
import org.asamk.signal.manager.api.GroupIdV2;
import org.asamk.signal.manager.api.GroupInviteLinkUrl;
import org.asamk.signal.manager.api.GroupLinkState;
import org.asamk.signal.manager.api.GroupNotFoundException;
import org.asamk.signal.manager.api.GroupPermission;
import org.asamk.signal.manager.api.GroupSendingNotAllowedException;
import org.asamk.signal.manager.api.InactiveGroupLinkException;
import org.asamk.signal.manager.api.LastGroupAdminException;
import org.asamk.signal.manager.api.NotAGroupMemberException;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.PendingAdminApprovalException;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResult;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.jobs.SyncStorageJob;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.util.AttachmentUtils;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.Utils;
import org.signal.core.models.ServiceId;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsementsResponse;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.storage.protos.groups.GroupChangeResponse;
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroupJoinInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupChangeLog;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupResponse;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.exceptions.ConflictException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GroupHelper {

    private static final Logger logger = LoggerFactory.getLogger(GroupHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final Context context;

    public GroupHelper(final Context context) {
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
        this.context = context;
    }

    public GroupInfo getGroup(GroupId groupId) {
        return getGroup(groupId, false);
    }

    public void updateGroupSendEndorsements(GroupId groupId) {
        getGroup(groupId, true);
    }

    public List<GroupInfo> getGroups() {
        final var groups = account.getGroupStore().getGroups();
        groups.forEach(group -> fillOrUpdateGroup(group, false));
        return groups;
    }

    public List<GroupInfo> getGroups(Collection<GroupId> groupIds) {
        final var groups = account.getGroupStore()
                .getGroups()
                .stream()
                .filter(g -> groupIds.contains(g.getGroupId()))
                .toList();
        groups.forEach(group -> fillOrUpdateGroup(group, true));
        return groups;
    }

    public boolean isGroupBlocked(final GroupId groupId) {
        var group = getGroup(groupId);
        return group != null && group.isBlocked();
    }

    public void downloadGroupAvatar(GroupIdV1 groupId, SignalServiceAttachment avatar) {
        try {
            context.getAvatarStore()
                    .storeGroupAvatar(groupId,
                            outputStream -> context.getAttachmentHelper().retrieveAttachment(avatar, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download avatar for group {}, ignoring: {}", groupId.toBase64(), e.getMessage());
        }
    }

    public Optional<SignalServiceAttachmentStream> createGroupAvatarAttachment(GroupIdV1 groupId) throws IOException {
        final var streamDetails = context.getAvatarStore().retrieveGroupAvatar(groupId);
        if (streamDetails == null) {
            return Optional.empty();
        }

        final var uploadSpec = dependencies.getMessageSender().getResumableUploadSpec();
        return Optional.of(AttachmentUtils.createAttachmentStream(streamDetails, Optional.empty(), uploadSpec));
    }

    public GroupInfoV2 getOrMigrateGroup(
            final GroupMasterKey groupMasterKey,
            final int revision,
            final byte[] signedGroupChange,
            final boolean ignoreAvatars
    ) {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

        final var groupId = GroupUtils.getGroupIdV2(groupSecretParams);
        final var groupInfoV2 = account.getGroupStore().getGroupOrPartialMigrate(groupMasterKey, groupId);

        if (groupInfoV2.getGroup() == null || groupInfoV2.getGroup().revision < revision) {
            DecryptedGroup group = null;
            if (signedGroupChange != null
                    && groupInfoV2.getGroup() != null
                    && groupInfoV2.getGroup().revision + 1 == revision) {
                final var decryptedGroupChange = context.getGroupV2Helper()
                        .getDecryptedGroupChange(signedGroupChange, groupMasterKey);

                if (decryptedGroupChange != null) {
                    storeProfileKeyFromChange(decryptedGroupChange);
                    group = context.getGroupV2Helper()
                            .getUpdatedDecryptedGroup(groupInfoV2.getGroup(), decryptedGroupChange);
                }
            }
            if (group == null) {
                try {
                    final var response = context.getGroupV2Helper().getDecryptedGroup(groupSecretParams);

                    if (response != null) {
                        group = response.getGroup();
                        storeProfileKeysFromHistory(groupSecretParams, groupInfoV2, group);
                        handleDecryptedGroupResponse(groupInfoV2, response);
                    }
                } catch (NotAGroupMemberException ignored) {
                }
            }
            if (group != null) {
                storeProfileKeysFromMembers(group);
                final var avatar = group.avatar;
                if (!avatar.isEmpty() && !ignoreAvatars) {
                    downloadGroupAvatar(groupId, groupSecretParams, avatar);
                }
            }
            groupInfoV2.setGroup(group);
            account.getGroupStore().updateGroup(groupInfoV2);
            context.getJobExecutor().enqueueJob(new SyncStorageJob());
        }

        return groupInfoV2;
    }

    private void handleDecryptedGroupResponse(
            GroupInfoV2 groupInfoV2,
            final DecryptedGroupResponse decryptedGroupResponse
    ) {
        updateGroupEndorsements(groupInfoV2.getGroupId(),
                groupInfoV2.getMasterKey(),
                decryptedGroupResponse.getGroup(),
                decryptedGroupResponse.getGroupSendEndorsementsResponse());
    }

    private void handleGroupChangeResponse(
            final GroupInfoV2 groupInfoV2,
            final GroupChangeResponse groupChangeResponse
    ) {
        if (groupChangeResponse.group_send_endorsements_response.size() > 0) {
            try {
                final var groupSendEndorsementsResponse = new GroupSendEndorsementsResponse(groupChangeResponse.group_send_endorsements_response.toByteArray());

                updateGroupEndorsements(groupInfoV2.getGroupId(),
                        groupInfoV2.getMasterKey(),
                        groupInfoV2.getGroup(),
                        groupSendEndorsementsResponse);
            } catch (InvalidInputException e) {
                logger.debug("Unable to parse send endorsements response", e);
            }
        }
    }

    private void updateGroupEndorsements(
            final GroupIdV2 groupId,
            final GroupMasterKey masterKey,
            final DecryptedGroup group,
            final GroupSendEndorsementsResponse groupSendEndorsementsResponse
    ) {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(masterKey);
        final var groupEndorsements = dependencies.getGroupsV2Operations()
                .forGroup(groupSecretParams)
                .receiveGroupSendEndorsements(account.getAci(), group, groupSendEndorsementsResponse);

        if (groupEndorsements != null) {
            final var endorsements = groupEndorsements.getEndorsements()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(entry -> account.getRecipientResolver().resolveRecipient(entry.getKey()),
                            Map.Entry::getValue));
            account.getGroupStore().updateGroupEndorsements(groupId, groupEndorsements.getExpirationMs(), endorsements);
        }
    }

    public Pair<GroupId, SendGroupMessageResults> createGroup(
            String name,
            Set<RecipientId> members,
            String avatarFile
    ) throws IOException, AttachmentInvalidException {
        final var selfRecipientId = account.getSelfRecipientId();
        if (members != null && members.contains(selfRecipientId)) {
            members = new HashSet<>(members);
            members.remove(selfRecipientId);
        }

        final var avatarBytes = readAvatarBytes(avatarFile);
        var gv2Pair = context.getGroupV2Helper()
                .createGroup(name == null ? "" : name, members == null ? Set.of() : members, avatarBytes);

        if (gv2Pair == null) {
            throw new IOException("Failed to create group");
        }

        final var gv2 = gv2Pair.first();
        final var decryptedGroup = gv2Pair.second();

        gv2.setGroup(decryptedGroup.getGroup());
        handleDecryptedGroupResponse(gv2, decryptedGroup);

        gv2.setProfileSharingEnabled(true);
        if (avatarBytes != null) {
            context.getAvatarStore()
                    .storeGroupAvatar(gv2.getGroupId(), outputStream -> outputStream.write(avatarBytes));
        }

        account.getGroupStore().updateGroup(gv2);

        final var messageBuilder = getGroupUpdateMessageBuilder(gv2, null);

        final var result = sendGroupMessage(messageBuilder,
                gv2.getMembersIncludingPendingWithout(selfRecipientId),
                gv2);
        context.getJobExecutor().enqueueJob(new SyncStorageJob());
        return new Pair<>(gv2.getGroupId(), result);
    }

    private Pair<GroupId, SendGroupMessageResults> createGroupV1(
            final String name,
            final Set<RecipientId> members,
            final byte[] avatarBytes
    ) throws IOException, AttachmentInvalidException {
        final var selfRecipientId = account.getSelfRecipientId();
        var gv1 = new GroupInfoV1(GroupIdV1.createRandom());
        gv1.setProfileSharingEnabled(true);
        gv1.addMembers(List.of(selfRecipientId));
        final var result = updateGroupV1(gv1, name, members, avatarBytes);
        return new Pair<>(gv1.getGroupId(), result);
    }

    public SendGroupMessageResults updateGroup(
            final GroupId groupId,
            final String name,
            final String description,
            final Set<RecipientId> members,
            final Set<RecipientId> removeMembers,
            final Set<RecipientId> admins,
            final Set<RecipientId> removeAdmins,
            final Set<RecipientId> banMembers,
            final Set<RecipientId> unbanMembers,
            final boolean resetGroupLink,
            final GroupLinkState groupLinkState,
            final GroupPermission addMemberPermission,
            final GroupPermission editDetailsPermission,
            final String avatarFile,
            final Integer expirationTimer,
            final Boolean isAnnouncementGroup
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, GroupSendingNotAllowedException {
        var group = getGroupForUpdating(groupId);
        final var avatarBytes = readAvatarBytes(avatarFile);

        SendGroupMessageResults results;
        switch (group) {
            case GroupInfoV2 gv2 -> {
                try {
                    results = updateGroupV2(gv2,
                            name,
                            description,
                            members,
                            removeMembers,
                            admins,
                            removeAdmins,
                            banMembers,
                            unbanMembers,
                            resetGroupLink,
                            groupLinkState,
                            addMemberPermission,
                            editDetailsPermission,
                            avatarBytes,
                            expirationTimer,
                            isAnnouncementGroup);
                } catch (ConflictException e) {
                    // Detected conflicting update, refreshing group and trying again
                    group = getGroup(groupId, true);
                    results = updateGroupV2((GroupInfoV2) group,
                            name,
                            description,
                            members,
                            removeMembers,
                            admins,
                            removeAdmins,
                            banMembers,
                            unbanMembers,
                            resetGroupLink,
                            groupLinkState,
                            addMemberPermission,
                            editDetailsPermission,
                            avatarBytes,
                            expirationTimer,
                            isAnnouncementGroup);
                }
            }

            case GroupInfoV1 gv1 -> {
                results = updateGroupV1(gv1, name, members, avatarBytes);
                if (expirationTimer != null) {
                    setExpirationTimer(gv1, expirationTimer);
                }
            }
        }
        context.getJobExecutor().enqueueJob(new SyncStorageJob());
        return results;
    }

    public void updateGroupProfileKey(GroupIdV2 groupId) throws GroupNotFoundException, NotAGroupMemberException, IOException {
        var group = getGroupForUpdating(groupId);

        if (group instanceof GroupInfoV2 groupInfoV2) {
            Pair<DecryptedGroup, GroupChangeResponse> groupChangePair;
            try {
                groupChangePair = context.getGroupV2Helper().updateSelfProfileKey(groupInfoV2);
            } catch (ConflictException e) {
                // Detected conflicting update, refreshing group and trying again
                groupInfoV2 = (GroupInfoV2) getGroup(groupId, true);
                groupChangePair = context.getGroupV2Helper().updateSelfProfileKey(groupInfoV2);
            }
            if (groupChangePair != null) {
                sendUpdateGroupV2Message(groupInfoV2, groupChangePair.first(), groupChangePair.second());
            }
        }
    }

    public Pair<GroupId, SendGroupMessageResults> joinGroup(
            GroupInviteLinkUrl inviteLinkUrl
    ) throws IOException, InactiveGroupLinkException, PendingAdminApprovalException {
        final DecryptedGroupJoinInfo groupJoinInfo;
        try {
            groupJoinInfo = context.getGroupV2Helper()
                    .getDecryptedGroupJoinInfo(inviteLinkUrl.getGroupMasterKey(), inviteLinkUrl.getPassword());
        } catch (GroupLinkNotActiveException e) {
            throw new InactiveGroupLinkException("Group link inactive (reason: " + e.getReason() + ")", e);
        }
        if (groupJoinInfo.pendingAdminApproval) {
            throw new PendingAdminApprovalException("You have already requested to join the group.");
        }
        final var changeResponse = context.getGroupV2Helper()
                .joinGroup(inviteLinkUrl.getGroupMasterKey(), inviteLinkUrl.getPassword(), groupJoinInfo);
        final var group = getOrMigrateGroup(inviteLinkUrl.getGroupMasterKey(),
                groupJoinInfo.revision + 1,
                changeResponse.group_change == null ? null : changeResponse.group_change.encode(),
                false);

        if (group.getGroup() == null) {
            // Only requested member, can't send update to group members
            return new Pair<>(group.getGroupId(), new SendGroupMessageResults(0, List.of()));
        }

        final var result = sendUpdateGroupV2Message(group, group.getGroup(), changeResponse);

        context.getJobExecutor().enqueueJob(new SyncStorageJob());
        return new Pair<>(group.getGroupId(), result);
    }

    public SendGroupMessageResults quitGroup(
            final GroupId groupId,
            final Set<RecipientId> newAdmins
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
        context.getAvatarStore().deleteGroupAvatar(groupId);
        context.getJobExecutor().enqueueJob(new SyncStorageJob());
    }

    public void setGroupBlocked(final GroupId groupId, final boolean blocked) throws GroupNotFoundException {
        var group = getGroup(groupId);
        if (group == null) {
            throw new GroupNotFoundException(groupId);
        }

        group.setBlocked(blocked);
        account.getGroupStore().updateGroup(group);
        context.getJobExecutor().enqueueJob(new SyncStorageJob());
    }

    public SendGroupMessageResults sendGroupInfoRequest(GroupIdV1 groupId, RecipientId recipientId) throws IOException {
        var group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.REQUEST_INFO).withId(groupId.serialize());

        var messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group.build());

        // Send group info request message to the recipient who sent us a message with this groupId
        return sendGroupMessage(messageBuilder, Set.of(recipientId), new GroupInfoV1(groupId));
    }

    public SendGroupMessageResults sendGroupInfoMessage(
            GroupIdV1 groupId,
            RecipientId recipientId
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
        return sendGroupMessage(messageBuilder, Set.of(recipientId), g);
    }

    private GroupInfo getGroup(GroupId groupId, boolean forceUpdate) {
        final var group = account.getGroupStore().getGroup(groupId);
        fillOrUpdateGroup(group, forceUpdate);
        return group;
    }

    private void fillOrUpdateGroup(final GroupInfo group, final boolean forceUpdate) {
        if (!(group instanceof GroupInfoV2 groupInfoV2)) {
            return;
        }

        if (!forceUpdate && (groupInfoV2.isPermissionDenied() || groupInfoV2.getGroup() != null)) {
            return;
        }

        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        DecryptedGroup decryptedGroup;
        try {
            final var response = context.getGroupV2Helper().getDecryptedGroup(groupSecretParams);
            if (response == null) {
                return;
            }
            decryptedGroup = response.getGroup();
            handleDecryptedGroupResponse(groupInfoV2, response);
        } catch (NotAGroupMemberException e) {
            groupInfoV2.setPermissionDenied(true);
            account.getGroupStore().updateGroup(group);
            return;
        }

        try {
            storeProfileKeysFromHistory(groupSecretParams, groupInfoV2, decryptedGroup);
        } catch (NotAGroupMemberException ignored) {
        }
        storeProfileKeysFromMembers(decryptedGroup);
        final var avatar = decryptedGroup.avatar;
        if (!avatar.isEmpty()) {
            downloadGroupAvatar(groupInfoV2.getGroupId(), groupSecretParams, avatar);
        }
        groupInfoV2.setGroup(decryptedGroup);
        account.getGroupStore().updateGroup(group);
    }

    private void downloadGroupAvatar(GroupIdV2 groupId, GroupSecretParams groupSecretParams, String cdnKey) {
        try {
            context.getAvatarStore()
                    .storeGroupAvatar(groupId,
                            outputStream -> retrieveGroupV2Avatar(groupSecretParams, cdnKey, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download avatar for group {}, ignoring: {}", groupId.toBase64(), e.getMessage());
        }
    }

    private void retrieveGroupV2Avatar(
            GroupSecretParams groupSecretParams,
            String cdnKey,
            OutputStream outputStream
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
        for (var member : group.members) {
            final var serviceId = ServiceId.parseOrThrow(member.aciBytes);
            final var recipientId = account.getRecipientResolver().resolveRecipient(serviceId);
            final var profileStore = account.getProfileStore();
            if (profileStore.getProfileKey(recipientId) != null) {
                // We already have a profile key, not updating it from a non-authoritative source
                continue;
            }
            try {
                profileStore.storeProfileKey(recipientId, new ProfileKey(member.profileKey.toByteArray()));
            } catch (InvalidInputException ignored) {
            }
        }
    }

    private void storeProfileKeyFromChange(final DecryptedGroupChange decryptedGroupChange) {
        final var profileKeyFromChange = context.getGroupV2Helper()
                .getAuthoritativeProfileKeyFromChange(decryptedGroupChange);

        if (profileKeyFromChange != null) {
            final var serviceId = profileKeyFromChange.first();
            final var profileKey = profileKeyFromChange.second();
            final var recipientId = account.getRecipientResolver().resolveRecipient(serviceId);
            account.getProfileStore().storeProfileKey(recipientId, profileKey);
        }
    }

    private void storeProfileKeysFromHistory(
            final GroupSecretParams groupSecretParams,
            final GroupInfoV2 localGroup,
            final DecryptedGroup newDecryptedGroup
    ) throws NotAGroupMemberException {
        final var revisionWeWereAdded = context.getGroupV2Helper().findRevisionWeWereAdded(newDecryptedGroup);
        final var localRevision = localGroup.getGroup() == null ? 0 : localGroup.getGroup().revision;
        final var sendEndorsementsExpirationMs = account.getGroupStore()
                .getGroupEndorsementExpirationMs(localGroup.getGroupId());
        var fromRevision = Math.max(revisionWeWereAdded, localRevision);
        final var newProfileKeys = new HashMap<RecipientId, ProfileKey>();
        while (true) {
            final var page = context.getGroupV2Helper()
                    .getDecryptedGroupHistoryPage(groupSecretParams, fromRevision, sendEndorsementsExpirationMs);
            if (page == null) {
                break;
            }
            page.getChangeLogs()
                    .stream()
                    .map(DecryptedGroupChangeLog::getChange)
                    .filter(Objects::nonNull)
                    .map(context.getGroupV2Helper()::getAuthoritativeProfileKeyFromChange)
                    .filter(Objects::nonNull)
                    .forEach(p -> {
                        final var serviceId = p.first();
                        final var profileKey = p.second();
                        final var recipientId = account.getRecipientResolver().resolveRecipient(serviceId);
                        newProfileKeys.put(recipientId, profileKey);
                    });
            if (!page.getPagingData().getHasMorePages()) {
                break;
            }
            fromRevision = page.getPagingData().getNextPageRevision();
        }

        newProfileKeys.entrySet()
                .stream()
                .filter(entry -> account.getProfileStore().getProfileKey(entry.getKey()) == null)
                .forEach(entry -> account.getProfileStore().storeProfileKey(entry.getKey(), entry.getValue()));
    }

    private GroupInfo getGroupForUpdating(GroupId groupId) throws GroupNotFoundException, NotAGroupMemberException {
        var g = getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        if (!g.isMember(account.getSelfRecipientId()) && !g.isPendingMember(account.getSelfRecipientId())) {
            throw new NotAGroupMemberException(groupId, g.getTitle());
        }
        if (groupId instanceof GroupIdV2) {
            // Refresh group before updating
            return getGroup(groupId, true);
        }
        return g;
    }

    private SendGroupMessageResults updateGroupV1(
            final GroupInfoV1 gv1,
            final String name,
            final Set<RecipientId> members,
            final byte[] avatarFile
    ) throws IOException, AttachmentInvalidException {
        updateGroupV1Details(gv1, name, members, avatarFile);

        account.getGroupStore().updateGroup(gv1);

        var messageBuilder = getGroupUpdateMessageBuilder(gv1);
        return sendGroupMessage(messageBuilder,
                gv1.getMembersIncludingPendingWithout(account.getSelfRecipientId()),
                gv1);
    }

    private void updateGroupV1Details(
            final GroupInfoV1 g,
            final String name,
            final Collection<RecipientId> members,
            final byte[] avatarFile
    ) throws IOException {
        if (name != null) {
            g.name = name;
        }

        if (members != null) {
            g.addMembers(members);
        }

        if (avatarFile != null) {
            context.getAvatarStore().storeGroupAvatar(g.getGroupId(), outputStream -> outputStream.write(avatarFile));
        }
    }

    /**
     * Change the expiration timer for a group
     */
    private void setExpirationTimer(
            GroupInfoV1 groupInfoV1,
            int messageExpirationTimer
    ) throws NotAGroupMemberException, GroupNotFoundException, IOException, GroupSendingNotAllowedException {
        groupInfoV1.messageExpirationTime = messageExpirationTimer;
        account.getGroupStore().updateGroup(groupInfoV1);
        sendExpirationTimerUpdate(groupInfoV1.getGroupId());
    }

    private void sendExpirationTimerUpdate(GroupIdV1 groupId) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        final var messageBuilder = SignalServiceDataMessage.newBuilder().asExpirationUpdate();
        context.getSendHelper().sendAsGroupMessage(messageBuilder, groupId, false, Optional.empty(), true);
    }

    private SendGroupMessageResults updateGroupV2(
            final GroupInfoV2 group,
            final String name,
            final String description,
            final Set<RecipientId> members,
            final Set<RecipientId> removeMembers,
            final Set<RecipientId> admins,
            final Set<RecipientId> removeAdmins,
            final Set<RecipientId> banMembers,
            final Set<RecipientId> unbanMembers,
            final boolean resetGroupLink,
            final GroupLinkState groupLinkState,
            final GroupPermission addMemberPermission,
            final GroupPermission editDetailsPermission,
            final byte[] avatarFile,
            final Integer expirationTimer,
            final Boolean isAnnouncementGroup
    ) throws IOException {
        SendGroupMessageResults result = null;
        final var groupV2Helper = context.getGroupV2Helper();
        if (group.isPendingMember(account.getSelfRecipientId())) {
            var groupGroupChangePair = groupV2Helper.acceptInvite(group);
            result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
        }

        if (members != null) {
            final var requestingMembers = new HashSet<>(members);
            requestingMembers.retainAll(group.getRequestingMembers());
            if (!requestingMembers.isEmpty()) {
                var groupGroupChangePair = groupV2Helper.approveJoinRequestMembers(group, requestingMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }
            final var newMembers = new HashSet<>(members);
            newMembers.removeAll(group.getMembers());
            newMembers.removeAll(group.getRequestingMembers());
            if (!newMembers.isEmpty()) {
                var groupGroupChangePair = groupV2Helper.addMembers(group, newMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }
        }

        if (removeMembers != null) {
            var existingRemoveMembers = new HashSet<>(removeMembers);
            if (banMembers != null) {
                existingRemoveMembers.addAll(banMembers);
            }
            existingRemoveMembers.retainAll(group.getMembers());
            if (members != null) {
                existingRemoveMembers.removeAll(members);
            }
            existingRemoveMembers.remove(account.getSelfRecipientId());// self can be removed with sendQuitGroupMessage
            if (!existingRemoveMembers.isEmpty()) {
                var groupGroupChangePair = groupV2Helper.removeMembers(group, existingRemoveMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }

            var pendingRemoveMembers = new HashSet<>(removeMembers);
            pendingRemoveMembers.retainAll(group.getPendingMembers());
            if (!pendingRemoveMembers.isEmpty()) {
                var groupGroupChangePair = groupV2Helper.revokeInvitedMembers(group, pendingRemoveMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }
            var requestingRemoveMembers = new HashSet<>(removeMembers);
            requestingRemoveMembers.retainAll(group.getRequestingMembers());
            if (!requestingRemoveMembers.isEmpty()) {
                var groupGroupChangePair = groupV2Helper.refuseJoinRequestMembers(group, requestingRemoveMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }
        }

        if (admins != null) {
            final var newAdmins = new HashSet<>(admins);
            newAdmins.retainAll(group.getMembers());
            newAdmins.removeAll(group.getAdminMembers());
            if (!newAdmins.isEmpty()) {
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
            if (!existingRemoveAdmins.isEmpty()) {
                for (var admin : existingRemoveAdmins) {
                    var groupGroupChangePair = groupV2Helper.setMemberAdmin(group, admin, false);
                    result = sendUpdateGroupV2Message(group,
                            groupGroupChangePair.first(),
                            groupGroupChangePair.second());
                }
            }
        }

        if (banMembers != null) {
            final var newlyBannedMembers = new HashSet<>(banMembers);
            newlyBannedMembers.removeAll(group.getBannedMembers());
            if (!newlyBannedMembers.isEmpty()) {
                var groupGroupChangePair = groupV2Helper.banMembers(group, newlyBannedMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
            }
        }

        if (unbanMembers != null) {
            var existingUnbanMembers = new HashSet<>(unbanMembers);
            existingUnbanMembers.retainAll(group.getBannedMembers());
            if (!existingUnbanMembers.isEmpty()) {
                var groupGroupChangePair = groupV2Helper.unbanMembers(group, existingUnbanMembers);
                result = sendUpdateGroupV2Message(group, groupGroupChangePair.first(), groupGroupChangePair.second());
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
                context.getAvatarStore()
                        .storeGroupAvatar(group.getGroupId(), outputStream -> outputStream.write(avatarFile));
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
                groupInfoV1.getMembersIncludingPendingWithout(account.getSelfRecipientId()),
                groupInfoV1);
    }

    private SendGroupMessageResults quitGroupV2(
            final GroupInfoV2 groupInfoV2,
            final Set<RecipientId> newAdmins
    ) throws LastGroupAdminException, IOException {
        final var currentAdmins = groupInfoV2.getAdminMembers();
        newAdmins.removeAll(currentAdmins);
        newAdmins.retainAll(groupInfoV2.getMembers());
        if (currentAdmins.contains(account.getSelfRecipientId())
                && currentAdmins.size() == 1
                && groupInfoV2.getMembers().size() > 1
                && newAdmins.isEmpty()) {
            // Last admin can't leave the group, unless she's also the last member
            throw new LastGroupAdminException(groupInfoV2.getGroupId(), groupInfoV2.getTitle());
        }
        final var groupGroupChangePair = context.getGroupV2Helper().leaveGroup(groupInfoV2, newAdmins);
        groupInfoV2.setGroup(groupGroupChangePair.first());
        account.getGroupStore().updateGroup(groupInfoV2);

        final var groupChangeResponse = groupGroupChangePair.second();
        handleGroupChangeResponse(groupInfoV2, groupChangeResponse);
        if (groupChangeResponse.group_change == null) {
            throw new AssertionError("groupChange is null");
        }
        var messageBuilder = getGroupUpdateMessageBuilder(groupInfoV2, groupChangeResponse.group_change.encode());
        return sendGroupMessage(messageBuilder,
                groupInfoV2.getMembersIncludingPendingWithout(account.getSelfRecipientId()),
                groupInfoV2);
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfoV1 g) throws AttachmentInvalidException {
        var group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                .withId(g.getGroupId().serialize())
                .withName(g.name)
                .withMembers(g.getMembers()
                        .stream()
                        .map(context.getRecipientHelper()::resolveSignalServiceAddress)
                        .toList());

        try {
            final var attachment = createGroupAvatarAttachment(g.getGroupId());
            attachment.ifPresent(group::withAvatar);
        } catch (IOException e) {
            throw new AttachmentInvalidException(g.getGroupId().toBase64(), e);
        }

        return SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build())
                .withExpiration(g.getMessageExpirationTimer());
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfoV2 g, byte[] signedGroupChange) {
        var group = SignalServiceGroupV2.newBuilder(g.getMasterKey())
                .withRevision(g.getGroup().revision)
                .withSignedGroupChange(signedGroupChange);
        return SignalServiceDataMessage.newBuilder()
                .asGroupMessage(group.build())
                .withExpiration(g.getMessageExpirationTimer());
    }

    private SendGroupMessageResults sendUpdateGroupV2Message(
            GroupInfoV2 group,
            DecryptedGroup newDecryptedGroup,
            GroupChangeResponse groupChangeResponse
    ) throws IOException {
        final var selfRecipientId = account.getSelfRecipientId();
        final var members = group.getMembersIncludingPendingWithout(selfRecipientId);
        group.setGroup(newDecryptedGroup);
        handleGroupChangeResponse(group, groupChangeResponse);
        members.addAll(group.getMembersIncludingPendingWithout(selfRecipientId));
        account.getGroupStore().updateGroup(group);

        if (groupChangeResponse.group_change == null) {
            throw new AssertionError("groupChange is null");
        }
        final var messageBuilder = getGroupUpdateMessageBuilder(group, groupChangeResponse.group_change.encode());
        return sendGroupMessage(messageBuilder, members, group);
    }

    private SendGroupMessageResults sendGroupMessage(
            final SignalServiceDataMessage.Builder messageBuilder,
            final Set<RecipientId> members,
            final GroupInfo groupInfo
    ) throws IOException {
        final var timestamp = System.currentTimeMillis();
        messageBuilder.withTimestamp(timestamp);
        final var results = context.getSendHelper().sendGroupMessage(messageBuilder.build(), members, groupInfo);
        return new SendGroupMessageResults(timestamp,
                results.stream()
                        .map(sendMessageResult -> SendMessageResult.from(sendMessageResult,
                                account.getRecipientResolver(),
                                account.getRecipientAddressResolver()))
                        .toList());
    }

    private byte[] readAvatarBytes(final String avatarFile) throws IOException {
        if (avatarFile == null) {
            return null;
        }
        try (final var avatar = Utils.createStreamDetails(avatarFile).first()) {
            return IOUtils.readFully(avatar.getStream());
        }
    }
}
