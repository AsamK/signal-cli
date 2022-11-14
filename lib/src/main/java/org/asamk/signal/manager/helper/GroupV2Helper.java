package org.asamk.signal.manager.helper;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.groups.GroupLinkPassword;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.util.Utils;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPniResponse;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.libsignal.zkgroup.groups.UuidCiphertext;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;
import org.whispersystems.signalservice.api.groupsv2.GroupHistoryPage;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class GroupV2Helper {

    private final static Logger logger = LoggerFactory.getLogger(GroupV2Helper.class);

    private final SignalDependencies dependencies;
    private final Context context;

    private HashMap<Long, AuthCredentialWithPniResponse> groupApiCredentials;

    GroupV2Helper(final Context context) {
        this.dependencies = context.getDependencies();
        this.context = context;
    }

    void clearAuthCredentialCache() {
        groupApiCredentials = null;
    }

    DecryptedGroup getDecryptedGroup(final GroupSecretParams groupSecretParams) throws NotAGroupMemberException {
        try {
            final var groupsV2AuthorizationString = getGroupAuthForToday(groupSecretParams);
            return dependencies.getGroupsV2Api().getGroup(groupSecretParams, groupsV2AuthorizationString);
        } catch (NonSuccessfulResponseCodeException e) {
            if (e.getCode() == 403) {
                throw new NotAGroupMemberException(GroupUtils.getGroupIdV2(groupSecretParams), null);
            }
            logger.warn("Failed to retrieve Group V2 info, ignoring: {}", e.getMessage());
            return null;
        } catch (IOException | VerificationFailedException | InvalidGroupStateException e) {
            logger.warn("Failed to retrieve Group V2 info, ignoring: {}", e.getMessage());
            return null;
        }
    }

    DecryptedGroupJoinInfo getDecryptedGroupJoinInfo(
            GroupMasterKey groupMasterKey, GroupLinkPassword password
    ) throws IOException, GroupLinkNotActiveException {
        var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

        return dependencies.getGroupsV2Api()
                .getGroupJoinInfo(groupSecretParams,
                        Optional.ofNullable(password).map(GroupLinkPassword::serialize),
                        getGroupAuthForToday(groupSecretParams));
    }

    GroupHistoryPage getDecryptedGroupHistoryPage(
            final GroupSecretParams groupSecretParams, int fromRevision
    ) throws NotAGroupMemberException {
        try {
            final var groupsV2AuthorizationString = getGroupAuthForToday(groupSecretParams);
            return dependencies.getGroupsV2Api()
                    .getGroupHistoryPage(groupSecretParams, fromRevision, groupsV2AuthorizationString, false);
        } catch (NonSuccessfulResponseCodeException e) {
            if (e.getCode() == 403) {
                throw new NotAGroupMemberException(GroupUtils.getGroupIdV2(groupSecretParams), null);
            }
            logger.warn("Failed to retrieve Group V2 history, ignoring: {}", e.getMessage());
            return null;
        } catch (IOException | VerificationFailedException | InvalidGroupStateException e) {
            logger.warn("Failed to retrieve Group V2 history, ignoring: {}", e.getMessage());
            return null;
        }
    }

    int findRevisionWeWereAdded(DecryptedGroup partialDecryptedGroup) {
        ByteString bytes = UuidUtil.toByteString(getSelfAci().uuid());
        for (DecryptedMember decryptedMember : partialDecryptedGroup.getMembersList()) {
            if (decryptedMember.getUuid().equals(bytes)) {
                return decryptedMember.getJoinedAtRevision();
            }
        }
        return partialDecryptedGroup.getRevision();
    }

    Pair<GroupInfoV2, DecryptedGroup> createGroup(
            String name, Set<RecipientId> members, byte[] avatarFile
    ) {
        final var newGroup = buildNewGroup(name, members, avatarFile);
        if (newGroup == null) {
            return null;
        }

        final var groupSecretParams = newGroup.getGroupSecretParams();

        final GroupsV2AuthorizationString groupAuthForToday;
        final DecryptedGroup decryptedGroup;
        try {
            groupAuthForToday = getGroupAuthForToday(groupSecretParams);
            dependencies.getGroupsV2Api().putNewGroup(newGroup, groupAuthForToday);
            decryptedGroup = dependencies.getGroupsV2Api().getGroup(groupSecretParams, groupAuthForToday);
        } catch (IOException | VerificationFailedException | InvalidGroupStateException e) {
            logger.warn("Failed to create V2 group: {}", e.getMessage());
            return null;
        }
        if (decryptedGroup == null) {
            logger.warn("Failed to create V2 group, unknown error!");
            return null;
        }

        final var groupId = GroupUtils.getGroupIdV2(groupSecretParams);
        final var masterKey = groupSecretParams.getMasterKey();
        var g = new GroupInfoV2(groupId, masterKey, context.getAccount().getRecipientResolver());

        return new Pair<>(g, decryptedGroup);
    }

    private GroupsV2Operations.NewGroup buildNewGroup(
            String name, Set<RecipientId> members, byte[] avatar
    ) {
        final var profileKeyCredential = context.getProfileHelper()
                .getExpiringProfileKeyCredential(context.getAccount().getSelfRecipientId());
        if (profileKeyCredential == null) {
            logger.warn("Cannot create a V2 group as self does not have a versioned profile");
            return null;
        }

        final var self = new GroupCandidate(getSelfAci().uuid(), Optional.of(profileKeyCredential));
        final var memberList = new ArrayList<>(members);
        final var credentials = context.getProfileHelper().getExpiringProfileKeyCredential(memberList).stream();
        final var uuids = memberList.stream()
                .map(member -> context.getRecipientHelper().resolveSignalServiceAddress(member).getServiceId().uuid());
        var candidates = Utils.zip(uuids,
                        credentials,
                        (uuid, credential) -> new GroupCandidate(uuid, Optional.ofNullable(credential)))
                .collect(Collectors.toSet());

        final var groupSecretParams = GroupSecretParams.generate();
        return dependencies.getGroupsV2Operations()
                .createNewGroup(groupSecretParams,
                        name,
                        Optional.ofNullable(avatar),
                        self,
                        candidates,
                        Member.Role.DEFAULT,
                        0);
    }

    Pair<DecryptedGroup, GroupChange> updateGroup(
            GroupInfoV2 groupInfoV2, String name, String description, byte[] avatarFile
    ) throws IOException {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        var groupOperations = dependencies.getGroupsV2Operations().forGroup(groupSecretParams);

        var change = name != null ? groupOperations.createModifyGroupTitle(name) : GroupChange.Actions.newBuilder();

        if (description != null) {
            change.setModifyDescription(groupOperations.createModifyGroupDescriptionAction(description));
        }

        if (avatarFile != null) {
            var avatarCdnKey = dependencies.getGroupsV2Api()
                    .uploadAvatar(avatarFile, groupSecretParams, getGroupAuthForToday(groupSecretParams));
            change.setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder().setAvatar(avatarCdnKey));
        }

        change.setSourceUuid(getSelfAci().toByteString());

        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> addMembers(
            GroupInfoV2 groupInfoV2, Set<RecipientId> newMembers
    ) throws IOException {
        GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        final var memberList = new ArrayList<>(newMembers);
        final var credentials = context.getProfileHelper().getExpiringProfileKeyCredential(memberList).stream();
        final var uuids = memberList.stream()
                .map(member -> context.getRecipientHelper().resolveSignalServiceAddress(member).getServiceId().uuid());
        var candidates = Utils.zip(uuids,
                        credentials,
                        (uuid, credential) -> new GroupCandidate(uuid, Optional.ofNullable(credential)))
                .collect(Collectors.toSet());
        final var bannedUuids = groupInfoV2.getBannedMembers()
                .stream()
                .map(member -> context.getRecipientHelper().resolveSignalServiceAddress(member).getServiceId().uuid())
                .collect(Collectors.toSet());

        final var aci = getSelfAci();
        final var change = groupOperations.createModifyGroupMembershipChange(candidates, bannedUuids, aci.uuid());

        change.setSourceUuid(getSelfAci().toByteString());

        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> leaveGroup(
            GroupInfoV2 groupInfoV2, Set<RecipientId> membersToMakeAdmin
    ) throws IOException {
        var pendingMembersList = groupInfoV2.getGroup().getPendingMembersList();
        final var selfAci = getSelfAci();
        var selfPendingMember = DecryptedGroupUtil.findPendingByUuid(pendingMembersList, selfAci.uuid());

        if (selfPendingMember.isPresent()) {
            return revokeInvites(groupInfoV2, Set.of(selfPendingMember.get()));
        }

        final var adminUuids = membersToMakeAdmin.stream()
                .map(context.getRecipientHelper()::resolveSignalServiceAddress)
                .map(SignalServiceAddress::getServiceId)
                .map(ServiceId::uuid)
                .toList();
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        return commitChange(groupInfoV2,
                groupOperations.createLeaveAndPromoteMembersToAdmin(selfAci.uuid(), adminUuids));
    }

    Pair<DecryptedGroup, GroupChange> removeMembers(
            GroupInfoV2 groupInfoV2, Set<RecipientId> members
    ) throws IOException {
        final var memberUuids = members.stream()
                .map(context.getRecipientHelper()::resolveSignalServiceAddress)
                .map(SignalServiceAddress::getServiceId)
                .map(ServiceId::uuid)
                .collect(Collectors.toSet());
        return ejectMembers(groupInfoV2, memberUuids);
    }

    Pair<DecryptedGroup, GroupChange> approveJoinRequestMembers(
            GroupInfoV2 groupInfoV2, Set<RecipientId> members
    ) throws IOException {
        final var memberUuids = members.stream()
                .map(context.getRecipientHelper()::resolveSignalServiceAddress)
                .map(SignalServiceAddress::getServiceId)
                .map(ServiceId::uuid)
                .collect(Collectors.toSet());
        return approveJoinRequest(groupInfoV2, memberUuids);
    }

    Pair<DecryptedGroup, GroupChange> refuseJoinRequestMembers(
            GroupInfoV2 groupInfoV2, Set<RecipientId> members
    ) throws IOException {
        final var memberUuids = members.stream()
                .map(context.getRecipientHelper()::resolveSignalServiceAddress)
                .map(SignalServiceAddress::getServiceId)
                .map(ServiceId::uuid)
                .collect(Collectors.toSet());
        return refuseJoinRequest(groupInfoV2, memberUuids);
    }

    Pair<DecryptedGroup, GroupChange> revokeInvitedMembers(
            GroupInfoV2 groupInfoV2, Set<RecipientId> members
    ) throws IOException {
        var pendingMembersList = groupInfoV2.getGroup().getPendingMembersList();
        final var memberUuids = members.stream()
                .map(context.getRecipientHelper()::resolveSignalServiceAddress)
                .map(SignalServiceAddress::getServiceId)
                .map(ServiceId::uuid)
                .map(uuid -> DecryptedGroupUtil.findPendingByUuid(pendingMembersList, uuid))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        return revokeInvites(groupInfoV2, memberUuids);
    }

    Pair<DecryptedGroup, GroupChange> banMembers(
            GroupInfoV2 groupInfoV2, Set<RecipientId> block
    ) throws IOException {
        GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        final var uuids = block.stream()
                .map(member -> context.getRecipientHelper().resolveSignalServiceAddress(member).getServiceId().uuid())
                .collect(Collectors.toSet());

        final var change = groupOperations.createBanUuidsChange(uuids,
                false,
                groupInfoV2.getGroup().getBannedMembersList());

        change.setSourceUuid(getSelfAci().toByteString());

        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> unbanMembers(
            GroupInfoV2 groupInfoV2, Set<RecipientId> block
    ) throws IOException {
        GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        final var uuids = block.stream()
                .map(member -> context.getRecipientHelper().resolveSignalServiceAddress(member).getServiceId().uuid())
                .collect(Collectors.toSet());

        final var change = groupOperations.createUnbanUuidsChange(uuids);

        change.setSourceUuid(getSelfAci().toByteString());

        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> resetGroupLinkPassword(GroupInfoV2 groupInfoV2) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        final var newGroupLinkPassword = GroupLinkPassword.createNew().serialize();
        final var change = groupOperations.createModifyGroupLinkPasswordChange(newGroupLinkPassword);
        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> setGroupLinkState(
            GroupInfoV2 groupInfoV2, GroupLinkState state
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        final var accessRequired = toAccessControl(state);
        final var requiresNewPassword = state != GroupLinkState.DISABLED && groupInfoV2.getGroup()
                .getInviteLinkPassword()
                .isEmpty();

        final var change = requiresNewPassword ? groupOperations.createModifyGroupLinkPasswordAndRightsChange(
                GroupLinkPassword.createNew().serialize(),
                accessRequired) : groupOperations.createChangeJoinByLinkRights(accessRequired);
        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> setEditDetailsPermission(
            GroupInfoV2 groupInfoV2, GroupPermission permission
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        final var accessRequired = toAccessControl(permission);
        final var change = groupOperations.createChangeAttributesRights(accessRequired);
        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> setAddMemberPermission(
            GroupInfoV2 groupInfoV2, GroupPermission permission
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        final var accessRequired = toAccessControl(permission);
        final var change = groupOperations.createChangeMembershipRights(accessRequired);
        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> updateSelfProfileKey(GroupInfoV2 groupInfoV2) throws IOException {
        Optional<DecryptedMember> selfInGroup = groupInfoV2.getGroup() == null
                ? Optional.empty()
                : DecryptedGroupUtil.findMemberByUuid(groupInfoV2.getGroup().getMembersList(), getSelfAci().uuid());
        if (selfInGroup.isEmpty()) {
            logger.trace("Not updating group, self not in group " + groupInfoV2.getGroupId().toBase64());
            return null;
        }

        final var profileKey = context.getAccount().getProfileKey();
        if (Arrays.equals(profileKey.serialize(), selfInGroup.get().getProfileKey().toByteArray())) {
            logger.trace("Not updating group, own Profile Key is already up to date in group "
                    + groupInfoV2.getGroupId().toBase64());
            return null;
        }
        logger.debug("Updating own profile key in group " + groupInfoV2.getGroupId().toBase64());

        final var selfRecipientId = context.getAccount().getSelfRecipientId();
        final var profileKeyCredential = context.getProfileHelper().getExpiringProfileKeyCredential(selfRecipientId);
        if (profileKeyCredential == null) {
            logger.trace("Cannot update profile key as self does not have a versioned profile");
            return null;
        }

        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        final var change = groupOperations.createUpdateProfileKeyCredentialChange(profileKeyCredential);
        change.setSourceUuid(getSelfAci().toByteString());
        return commitChange(groupInfoV2, change);
    }

    GroupChange joinGroup(
            GroupMasterKey groupMasterKey,
            GroupLinkPassword groupLinkPassword,
            DecryptedGroupJoinInfo decryptedGroupJoinInfo
    ) throws IOException {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
        final var groupOperations = dependencies.getGroupsV2Operations().forGroup(groupSecretParams);

        final var selfRecipientId = context.getAccount().getSelfRecipientId();
        final var profileKeyCredential = context.getProfileHelper().getExpiringProfileKeyCredential(selfRecipientId);
        if (profileKeyCredential == null) {
            throw new IOException("Cannot join a V2 group as self does not have a versioned profile");
        }

        var requestToJoin = decryptedGroupJoinInfo.getAddFromInviteLink() == AccessControl.AccessRequired.ADMINISTRATOR;
        var change = requestToJoin
                ? groupOperations.createGroupJoinRequest(profileKeyCredential)
                : groupOperations.createGroupJoinDirect(profileKeyCredential);

        change.setSourceUuid(context.getRecipientHelper()
                .resolveSignalServiceAddress(selfRecipientId)
                .getServiceId()
                .toByteString());

        return commitChange(groupSecretParams, decryptedGroupJoinInfo.getRevision(), change, groupLinkPassword);
    }

    Pair<DecryptedGroup, GroupChange> acceptInvite(GroupInfoV2 groupInfoV2) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        final var selfRecipientId = context.getAccount().getSelfRecipientId();
        final var profileKeyCredential = context.getProfileHelper().getExpiringProfileKeyCredential(selfRecipientId);
        if (profileKeyCredential == null) {
            throw new IOException("Cannot join a V2 group as self does not have a versioned profile");
        }

        final var change = groupOperations.createAcceptInviteChange(profileKeyCredential);

        final var aci = context.getRecipientHelper().resolveSignalServiceAddress(selfRecipientId).getServiceId();
        change.setSourceUuid(aci.toByteString());

        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> setMemberAdmin(
            GroupInfoV2 groupInfoV2, RecipientId recipientId, boolean admin
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        final var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
        final var newRole = admin ? Member.Role.ADMINISTRATOR : Member.Role.DEFAULT;
        final var change = groupOperations.createChangeMemberRole(address.getServiceId().uuid(), newRole);
        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> setMessageExpirationTimer(
            GroupInfoV2 groupInfoV2, int messageExpirationTimer
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        final var change = groupOperations.createModifyGroupTimerChange(messageExpirationTimer);
        return commitChange(groupInfoV2, change);
    }

    Pair<DecryptedGroup, GroupChange> setIsAnnouncementGroup(
            GroupInfoV2 groupInfoV2, boolean isAnnouncementGroup
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        final var change = groupOperations.createAnnouncementGroupChange(isAnnouncementGroup);
        return commitChange(groupInfoV2, change);
    }

    private AccessControl.AccessRequired toAccessControl(final GroupLinkState state) {
        return switch (state) {
            case DISABLED -> AccessControl.AccessRequired.UNSATISFIABLE;
            case ENABLED -> AccessControl.AccessRequired.ANY;
            case ENABLED_WITH_APPROVAL -> AccessControl.AccessRequired.ADMINISTRATOR;
        };
    }

    private AccessControl.AccessRequired toAccessControl(final GroupPermission permission) {
        return switch (permission) {
            case EVERY_MEMBER -> AccessControl.AccessRequired.MEMBER;
            case ONLY_ADMINS -> AccessControl.AccessRequired.ADMINISTRATOR;
        };
    }

    private GroupsV2Operations.GroupOperations getGroupOperations(final GroupInfoV2 groupInfoV2) {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        return dependencies.getGroupsV2Operations().forGroup(groupSecretParams);
    }

    private Pair<DecryptedGroup, GroupChange> revokeInvites(
            GroupInfoV2 groupInfoV2, Set<DecryptedPendingMember> pendingMembers
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        final var uuidCipherTexts = pendingMembers.stream().map(member -> {
            try {
                return new UuidCiphertext(member.getUuidCipherText().toByteArray());
            } catch (InvalidInputException e) {
                throw new AssertionError(e);
            }
        }).collect(Collectors.toSet());
        return commitChange(groupInfoV2, groupOperations.createRemoveInvitationChange(uuidCipherTexts));
    }

    private Pair<DecryptedGroup, GroupChange> approveJoinRequest(
            GroupInfoV2 groupInfoV2, Set<UUID> uuids
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        return commitChange(groupInfoV2, groupOperations.createApproveGroupJoinRequest(uuids));
    }

    private Pair<DecryptedGroup, GroupChange> refuseJoinRequest(
            GroupInfoV2 groupInfoV2, Set<UUID> uuids
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        return commitChange(groupInfoV2, groupOperations.createRefuseGroupJoinRequest(uuids, false, List.of()));
    }

    private Pair<DecryptedGroup, GroupChange> ejectMembers(
            GroupInfoV2 groupInfoV2, Set<UUID> uuids
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        return commitChange(groupInfoV2, groupOperations.createRemoveMembersChange(uuids, false, List.of()));
    }

    private Pair<DecryptedGroup, GroupChange> commitChange(
            GroupInfoV2 groupInfoV2, GroupChange.Actions.Builder change
    ) throws IOException {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        final var groupOperations = dependencies.getGroupsV2Operations().forGroup(groupSecretParams);
        final var previousGroupState = groupInfoV2.getGroup();
        final var nextRevision = previousGroupState.getRevision() + 1;
        final var changeActions = change.setRevision(nextRevision).build();
        final DecryptedGroupChange decryptedChange;
        final DecryptedGroup decryptedGroupState;

        try {
            decryptedChange = groupOperations.decryptChange(changeActions, getSelfAci().uuid());
            decryptedGroupState = DecryptedGroupUtil.apply(previousGroupState, decryptedChange);
        } catch (VerificationFailedException | InvalidGroupStateException | NotAbleToApplyGroupV2ChangeException e) {
            throw new IOException(e);
        }

        var signedGroupChange = dependencies.getGroupsV2Api()
                .patchGroup(changeActions, getGroupAuthForToday(groupSecretParams), Optional.empty());

        return new Pair<>(decryptedGroupState, signedGroupChange);
    }

    private GroupChange commitChange(
            GroupSecretParams groupSecretParams,
            int currentRevision,
            GroupChange.Actions.Builder change,
            GroupLinkPassword password
    ) throws IOException {
        final var nextRevision = currentRevision + 1;
        final var changeActions = change.setRevision(nextRevision).build();

        return dependencies.getGroupsV2Api()
                .patchGroup(changeActions,
                        getGroupAuthForToday(groupSecretParams),
                        Optional.ofNullable(password).map(GroupLinkPassword::serialize));
    }

    Pair<ServiceId, ProfileKey> getAuthoritativeProfileKeyFromChange(final DecryptedGroupChange change) {
        UUID editor = UuidUtil.fromByteStringOrNull(change.getEditor());
        final var editorProfileKeyBytes = Stream.concat(Stream.of(change.getNewMembersList().stream(),
                                change.getPromotePendingMembersList().stream(),
                                change.getModifiedProfileKeysList().stream())
                        .flatMap(Function.identity())
                        .filter(m -> UuidUtil.fromByteString(m.getUuid()).equals(editor))
                        .map(DecryptedMember::getProfileKey),
                change.getNewRequestingMembersList()
                        .stream()
                        .filter(m -> UuidUtil.fromByteString(m.getUuid()).equals(editor))
                        .map(DecryptedRequestingMember::getProfileKey)).findFirst();

        if (editorProfileKeyBytes.isEmpty()) {
            return null;
        }

        ProfileKey profileKey;
        try {
            profileKey = new ProfileKey(editorProfileKeyBytes.get().toByteArray());
        } catch (InvalidInputException e) {
            logger.debug("Bad profile key in group");
            return null;
        }

        return new Pair<>(ServiceId.from(editor), profileKey);
    }

    DecryptedGroup getUpdatedDecryptedGroup(DecryptedGroup group, DecryptedGroupChange decryptedGroupChange) {
        try {
            return DecryptedGroupUtil.apply(group, decryptedGroupChange);
        } catch (NotAbleToApplyGroupV2ChangeException e) {
            return null;
        }
    }

    DecryptedGroupChange getDecryptedGroupChange(byte[] signedGroupChange, GroupMasterKey groupMasterKey) {
        if (signedGroupChange != null) {
            var groupOperations = dependencies.getGroupsV2Operations()
                    .forGroup(GroupSecretParams.deriveFromMasterKey(groupMasterKey));

            try {
                return groupOperations.decryptChange(GroupChange.parseFrom(signedGroupChange), true).orElse(null);
            } catch (VerificationFailedException | InvalidGroupStateException | InvalidProtocolBufferException e) {
                return null;
            }
        }

        return null;
    }

    private static long currentDaySeconds() {
        return TimeUnit.DAYS.toSeconds(TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()));
    }

    private GroupsV2AuthorizationString getGroupAuthForToday(
            final GroupSecretParams groupSecretParams
    ) throws IOException {
        final var todaySeconds = currentDaySeconds();
        if (groupApiCredentials == null || !groupApiCredentials.containsKey(todaySeconds)) {
            // Returns credentials for the next 7 days
            groupApiCredentials = dependencies.getGroupsV2Api().getCredentials(todaySeconds);
            // TODO cache credentials on disk until they expire
        }
        try {
            return getAuthorizationString(groupSecretParams, todaySeconds);
        } catch (VerificationFailedException e) {
            logger.debug("Group api credentials invalid, renewing and trying again.");
            groupApiCredentials.clear();
        }

        groupApiCredentials = dependencies.getGroupsV2Api().getCredentials(todaySeconds);
        try {
            return getAuthorizationString(groupSecretParams, todaySeconds);
        } catch (VerificationFailedException e) {
            throw new IOException(e);
        }
    }

    private GroupsV2AuthorizationString getAuthorizationString(
            final GroupSecretParams groupSecretParams, final long todaySeconds
    ) throws VerificationFailedException {
        var authCredentialResponse = groupApiCredentials.get(todaySeconds);
        final var aci = getSelfAci();
        final var pni = getSelfPni();
        return dependencies.getGroupsV2Api()
                .getGroupsV2AuthorizationString(aci, pni, todaySeconds, groupSecretParams, authCredentialResponse);
    }

    private ACI getSelfAci() {
        return context.getAccount().getAci();
    }

    private PNI getSelfPni() {
        return context.getAccount().getPni();
    }
}
