package org.asamk.signal.manager.helper;

import com.google.protobuf.InvalidProtocolBufferException;

import org.asamk.signal.manager.groups.GroupLinkPassword;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.util.IOUtils;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GroupV2Helper {

    private final static Logger logger = LoggerFactory.getLogger(GroupV2Helper.class);

    private final ProfileKeyCredentialProvider profileKeyCredentialProvider;

    private final ProfileProvider profileProvider;

    private final SelfRecipientIdProvider selfRecipientIdProvider;

    private final GroupsV2Operations groupsV2Operations;

    private final GroupsV2Api groupsV2Api;

    private final GroupAuthorizationProvider groupAuthorizationProvider;

    private final SignalServiceAddressResolver addressResolver;

    public GroupV2Helper(
            final ProfileKeyCredentialProvider profileKeyCredentialProvider,
            final ProfileProvider profileProvider,
            final SelfRecipientIdProvider selfRecipientIdProvider,
            final GroupsV2Operations groupsV2Operations,
            final GroupsV2Api groupsV2Api,
            final GroupAuthorizationProvider groupAuthorizationProvider,
            final SignalServiceAddressResolver addressResolver
    ) {
        this.profileKeyCredentialProvider = profileKeyCredentialProvider;
        this.profileProvider = profileProvider;
        this.selfRecipientIdProvider = selfRecipientIdProvider;
        this.groupsV2Operations = groupsV2Operations;
        this.groupsV2Api = groupsV2Api;
        this.groupAuthorizationProvider = groupAuthorizationProvider;
        this.addressResolver = addressResolver;
    }

    public DecryptedGroup getDecryptedGroup(final GroupSecretParams groupSecretParams) {
        try {
            final var groupsV2AuthorizationString = groupAuthorizationProvider.getAuthorizationForToday(
                    groupSecretParams);
            return groupsV2Api.getGroup(groupSecretParams, groupsV2AuthorizationString);
        } catch (IOException | VerificationFailedException | InvalidGroupStateException e) {
            logger.warn("Failed to retrieve Group V2 info, ignoring: {}", e.getMessage());
            return null;
        }
    }

    public DecryptedGroupJoinInfo getDecryptedGroupJoinInfo(
            GroupMasterKey groupMasterKey, GroupLinkPassword password
    ) throws IOException, GroupLinkNotActiveException {
        var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

        return groupsV2Api.getGroupJoinInfo(groupSecretParams,
                Optional.fromNullable(password).transform(GroupLinkPassword::serialize),
                groupAuthorizationProvider.getAuthorizationForToday(groupSecretParams));
    }

    public Pair<GroupInfoV2, DecryptedGroup> createGroup(
            String name, Set<RecipientId> members, File avatarFile
    ) throws IOException {
        final var avatarBytes = readAvatarBytes(avatarFile);
        final var newGroup = buildNewGroup(name, members, avatarBytes);
        if (newGroup == null) {
            return null;
        }

        final var groupSecretParams = newGroup.getGroupSecretParams();

        final GroupsV2AuthorizationString groupAuthForToday;
        final DecryptedGroup decryptedGroup;
        try {
            groupAuthForToday = groupAuthorizationProvider.getAuthorizationForToday(groupSecretParams);
            groupsV2Api.putNewGroup(newGroup, groupAuthForToday);
            decryptedGroup = groupsV2Api.getGroup(groupSecretParams, groupAuthForToday);
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
        var g = new GroupInfoV2(groupId, masterKey);

        return new Pair<>(g, decryptedGroup);
    }

    private byte[] readAvatarBytes(final File avatarFile) throws IOException {
        final byte[] avatarBytes;
        try (InputStream avatar = avatarFile == null ? null : new FileInputStream(avatarFile)) {
            avatarBytes = avatar == null ? null : IOUtils.readFully(avatar);
        }
        return avatarBytes;
    }

    private GroupsV2Operations.NewGroup buildNewGroup(
            String name, Set<RecipientId> members, byte[] avatar
    ) {
        final var profileKeyCredential = profileKeyCredentialProvider.getProfileKeyCredential(selfRecipientIdProvider.getSelfRecipientId());
        if (profileKeyCredential == null) {
            logger.warn("Cannot create a V2 group as self does not have a versioned profile");
            return null;
        }

        if (!areMembersValid(members)) return null;

        var self = new GroupCandidate(addressResolver.resolveSignalServiceAddress(selfRecipientIdProvider.getSelfRecipientId())
                .getUuid()
                .orNull(), Optional.fromNullable(profileKeyCredential));
        var candidates = members.stream()
                .map(member -> new GroupCandidate(addressResolver.resolveSignalServiceAddress(member).getUuid().get(),
                        Optional.fromNullable(profileKeyCredentialProvider.getProfileKeyCredential(member))))
                .collect(Collectors.toSet());

        final var groupSecretParams = GroupSecretParams.generate();
        return groupsV2Operations.createNewGroup(groupSecretParams,
                name,
                Optional.fromNullable(avatar),
                self,
                candidates,
                Member.Role.DEFAULT,
                0);
    }

    private boolean areMembersValid(final Set<RecipientId> members) {
        final var noUuidCapability = members.stream()
                .map(addressResolver::resolveSignalServiceAddress)
                .filter(address -> !address.getUuid().isPresent())
                .map(SignalServiceAddress::getNumber)
                .map(Optional::get)
                .collect(Collectors.toSet());
        if (noUuidCapability.size() > 0) {
            logger.warn("Cannot create a V2 group as some members don't have a UUID: {}",
                    String.join(", ", noUuidCapability));
            return false;
        }

        final var noGv2Capability = members.stream()
                .map(profileProvider::getProfile)
                .filter(profile -> profile != null && !profile.getCapabilities().contains(Profile.Capability.gv2))
                .collect(Collectors.toSet());
        if (noGv2Capability.size() > 0) {
            logger.warn("Cannot create a V2 group as some members don't support Groups V2: {}",
                    noGv2Capability.stream().map(Profile::getDisplayName).collect(Collectors.joining(", ")));
            return false;
        }

        return true;
    }

    public Pair<DecryptedGroup, GroupChange> updateGroup(
            GroupInfoV2 groupInfoV2, String name, String description, File avatarFile
    ) throws IOException {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        var groupOperations = groupsV2Operations.forGroup(groupSecretParams);

        var change = name != null ? groupOperations.createModifyGroupTitle(name) : GroupChange.Actions.newBuilder();

        if (description != null) {
            change.setModifyDescription(groupOperations.createModifyGroupDescription(description));
        }

        if (avatarFile != null) {
            final var avatarBytes = readAvatarBytes(avatarFile);
            var avatarCdnKey = groupsV2Api.uploadAvatar(avatarBytes,
                    groupSecretParams,
                    groupAuthorizationProvider.getAuthorizationForToday(groupSecretParams));
            change.setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder().setAvatar(avatarCdnKey));
        }

        final var uuid = addressResolver.resolveSignalServiceAddress(this.selfRecipientIdProvider.getSelfRecipientId())
                .getUuid();
        if (uuid.isPresent()) {
            change.setSourceUuid(UuidUtil.toByteString(uuid.get()));
        }

        return commitChange(groupInfoV2, change);
    }

    public Pair<DecryptedGroup, GroupChange> addMembers(
            GroupInfoV2 groupInfoV2, Set<RecipientId> newMembers
    ) throws IOException {
        GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        if (!areMembersValid(newMembers)) {
            throw new IOException("Failed to update group");
        }

        var candidates = newMembers.stream()
                .map(member -> new GroupCandidate(addressResolver.resolveSignalServiceAddress(member).getUuid().get(),
                        Optional.fromNullable(profileKeyCredentialProvider.getProfileKeyCredential(member))))
                .collect(Collectors.toSet());

        final var uuid = addressResolver.resolveSignalServiceAddress(selfRecipientIdProvider.getSelfRecipientId())
                .getUuid()
                .get();
        final var change = groupOperations.createModifyGroupMembershipChange(candidates, uuid);

        change.setSourceUuid(UuidUtil.toByteString(uuid));

        return commitChange(groupInfoV2, change);
    }

    public Pair<DecryptedGroup, GroupChange> leaveGroup(
            GroupInfoV2 groupInfoV2, Set<RecipientId> membersToMakeAdmin
    ) throws IOException {
        var pendingMembersList = groupInfoV2.getGroup().getPendingMembersList();
        final var selfUuid = addressResolver.resolveSignalServiceAddress(selfRecipientIdProvider.getSelfRecipientId())
                .getUuid()
                .get();
        var selfPendingMember = DecryptedGroupUtil.findPendingByUuid(pendingMembersList, selfUuid);

        if (selfPendingMember.isPresent()) {
            return revokeInvites(groupInfoV2, Set.of(selfPendingMember.get()));
        }

        final var adminUuids = membersToMakeAdmin.stream()
                .map(addressResolver::resolveSignalServiceAddress)
                .map(SignalServiceAddress::getUuid)
                .map(Optional::get)
                .collect(Collectors.toList());
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        return commitChange(groupInfoV2, groupOperations.createLeaveAndPromoteMembersToAdmin(selfUuid, adminUuids));
    }

    public Pair<DecryptedGroup, GroupChange> removeMembers(
            GroupInfoV2 groupInfoV2, Set<RecipientId> members
    ) throws IOException {
        final var memberUuids = members.stream()
                .map(addressResolver::resolveSignalServiceAddress)
                .map(SignalServiceAddress::getUuid)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        return ejectMembers(groupInfoV2, memberUuids);
    }

    public Pair<DecryptedGroup, GroupChange> revokeInvitedMembers(
            GroupInfoV2 groupInfoV2, Set<RecipientId> members
    ) throws IOException {
        var pendingMembersList = groupInfoV2.getGroup().getPendingMembersList();
        final var memberUuids = members.stream()
                .map(addressResolver::resolveSignalServiceAddress)
                .map(SignalServiceAddress::getUuid)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(uuid -> DecryptedGroupUtil.findPendingByUuid(pendingMembersList, uuid))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        return revokeInvites(groupInfoV2, memberUuids);
    }

    public Pair<DecryptedGroup, GroupChange> resetGroupLinkPassword(GroupInfoV2 groupInfoV2) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        final var newGroupLinkPassword = GroupLinkPassword.createNew().serialize();
        final var change = groupOperations.createModifyGroupLinkPasswordChange(newGroupLinkPassword);
        return commitChange(groupInfoV2, change);
    }

    public Pair<DecryptedGroup, GroupChange> setGroupLinkState(
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

    public Pair<DecryptedGroup, GroupChange> setEditDetailsPermission(
            GroupInfoV2 groupInfoV2, GroupPermission permission
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        final var accessRequired = toAccessControl(permission);
        final var change = groupOperations.createChangeAttributesRights(accessRequired);
        return commitChange(groupInfoV2, change);
    }

    public Pair<DecryptedGroup, GroupChange> setAddMemberPermission(
            GroupInfoV2 groupInfoV2, GroupPermission permission
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        final var accessRequired = toAccessControl(permission);
        final var change = groupOperations.createChangeMembershipRights(accessRequired);
        return commitChange(groupInfoV2, change);
    }

    public GroupChange joinGroup(
            GroupMasterKey groupMasterKey,
            GroupLinkPassword groupLinkPassword,
            DecryptedGroupJoinInfo decryptedGroupJoinInfo
    ) throws IOException {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
        final var groupOperations = groupsV2Operations.forGroup(groupSecretParams);

        final var selfRecipientId = this.selfRecipientIdProvider.getSelfRecipientId();
        final var profileKeyCredential = profileKeyCredentialProvider.getProfileKeyCredential(selfRecipientId);
        if (profileKeyCredential == null) {
            throw new IOException("Cannot join a V2 group as self does not have a versioned profile");
        }

        var requestToJoin = decryptedGroupJoinInfo.getAddFromInviteLink() == AccessControl.AccessRequired.ADMINISTRATOR;
        var change = requestToJoin
                ? groupOperations.createGroupJoinRequest(profileKeyCredential)
                : groupOperations.createGroupJoinDirect(profileKeyCredential);

        change.setSourceUuid(UuidUtil.toByteString(addressResolver.resolveSignalServiceAddress(selfRecipientId)
                .getUuid()
                .get()));

        return commitChange(groupSecretParams, decryptedGroupJoinInfo.getRevision(), change, groupLinkPassword);
    }

    public Pair<DecryptedGroup, GroupChange> acceptInvite(GroupInfoV2 groupInfoV2) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);

        final var selfRecipientId = this.selfRecipientIdProvider.getSelfRecipientId();
        final var profileKeyCredential = profileKeyCredentialProvider.getProfileKeyCredential(selfRecipientId);
        if (profileKeyCredential == null) {
            throw new IOException("Cannot join a V2 group as self does not have a versioned profile");
        }

        final var change = groupOperations.createAcceptInviteChange(profileKeyCredential);

        final var uuid = addressResolver.resolveSignalServiceAddress(selfRecipientId).getUuid();
        if (uuid.isPresent()) {
            change.setSourceUuid(UuidUtil.toByteString(uuid.get()));
        }

        return commitChange(groupInfoV2, change);
    }

    public Pair<DecryptedGroup, GroupChange> setMemberAdmin(
            GroupInfoV2 groupInfoV2, RecipientId recipientId, boolean admin
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        final var address = addressResolver.resolveSignalServiceAddress(recipientId);
        final var newRole = admin ? Member.Role.ADMINISTRATOR : Member.Role.DEFAULT;
        final var change = groupOperations.createChangeMemberRole(address.getUuid().get(), newRole);
        return commitChange(groupInfoV2, change);
    }

    public Pair<DecryptedGroup, GroupChange> setMessageExpirationTimer(
            GroupInfoV2 groupInfoV2, int messageExpirationTimer
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        final var change = groupOperations.createModifyGroupTimerChange(messageExpirationTimer);
        return commitChange(groupInfoV2, change);
    }

    private AccessControl.AccessRequired toAccessControl(final GroupLinkState state) {
        switch (state) {
            case DISABLED:
                return AccessControl.AccessRequired.UNSATISFIABLE;
            case ENABLED:
                return AccessControl.AccessRequired.ANY;
            case ENABLED_WITH_APPROVAL:
                return AccessControl.AccessRequired.ADMINISTRATOR;
            default:
                throw new AssertionError();
        }
    }

    private AccessControl.AccessRequired toAccessControl(final GroupPermission permission) {
        switch (permission) {
            case EVERY_MEMBER:
                return AccessControl.AccessRequired.MEMBER;
            case ONLY_ADMINS:
                return AccessControl.AccessRequired.ADMINISTRATOR;
            default:
                throw new AssertionError();
        }
    }

    private GroupsV2Operations.GroupOperations getGroupOperations(final GroupInfoV2 groupInfoV2) {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        return groupsV2Operations.forGroup(groupSecretParams);
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

    private Pair<DecryptedGroup, GroupChange> ejectMembers(
            GroupInfoV2 groupInfoV2, Set<UUID> uuids
    ) throws IOException {
        final GroupsV2Operations.GroupOperations groupOperations = getGroupOperations(groupInfoV2);
        return commitChange(groupInfoV2, groupOperations.createRemoveMembersChange(uuids));
    }

    private Pair<DecryptedGroup, GroupChange> commitChange(
            GroupInfoV2 groupInfoV2, GroupChange.Actions.Builder change
    ) throws IOException {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        final var groupOperations = groupsV2Operations.forGroup(groupSecretParams);
        final var previousGroupState = groupInfoV2.getGroup();
        final var nextRevision = previousGroupState.getRevision() + 1;
        final var changeActions = change.setRevision(nextRevision).build();
        final DecryptedGroupChange decryptedChange;
        final DecryptedGroup decryptedGroupState;

        try {
            decryptedChange = groupOperations.decryptChange(changeActions,
                    addressResolver.resolveSignalServiceAddress(selfRecipientIdProvider.getSelfRecipientId())
                            .getUuid()
                            .get());
            decryptedGroupState = DecryptedGroupUtil.apply(previousGroupState, decryptedChange);
        } catch (VerificationFailedException | InvalidGroupStateException | NotAbleToApplyGroupV2ChangeException e) {
            throw new IOException(e);
        }

        var signedGroupChange = groupsV2Api.patchGroup(changeActions,
                groupAuthorizationProvider.getAuthorizationForToday(groupSecretParams),
                Optional.absent());

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

        return groupsV2Api.patchGroup(changeActions,
                groupAuthorizationProvider.getAuthorizationForToday(groupSecretParams),
                Optional.fromNullable(password).transform(GroupLinkPassword::serialize));
    }

    public DecryptedGroup getUpdatedDecryptedGroup(
            DecryptedGroup group, byte[] signedGroupChange, GroupMasterKey groupMasterKey
    ) {
        try {
            final var decryptedGroupChange = getDecryptedGroupChange(signedGroupChange, groupMasterKey);
            if (decryptedGroupChange == null) {
                return null;
            }
            return DecryptedGroupUtil.apply(group, decryptedGroupChange);
        } catch (NotAbleToApplyGroupV2ChangeException e) {
            return null;
        }
    }

    private DecryptedGroupChange getDecryptedGroupChange(byte[] signedGroupChange, GroupMasterKey groupMasterKey) {
        if (signedGroupChange != null) {
            var groupOperations = groupsV2Operations.forGroup(GroupSecretParams.deriveFromMasterKey(groupMasterKey));

            try {
                return groupOperations.decryptChange(GroupChange.parseFrom(signedGroupChange), true).orNull();
            } catch (VerificationFailedException | InvalidGroupStateException | InvalidProtocolBufferException e) {
                return null;
            }
        }

        return null;
    }
}
