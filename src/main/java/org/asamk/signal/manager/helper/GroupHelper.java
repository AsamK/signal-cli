package org.asamk.signal.manager.helper;

import com.google.protobuf.InvalidProtocolBufferException;

import org.asamk.signal.manager.GroupIdV2;
import org.asamk.signal.manager.GroupLinkPassword;
import org.asamk.signal.manager.GroupUtils;
import org.asamk.signal.storage.groups.GroupInfoV2;
import org.asamk.signal.util.IOUtils;
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
import org.signal.zkgroup.profiles.ProfileKeyCredential;
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GroupHelper {

    private final ProfileKeyCredentialProvider profileKeyCredentialProvider;

    private final ProfileProvider profileProvider;

    private final SelfAddressProvider selfAddressProvider;

    private final GroupsV2Operations groupsV2Operations;

    private final GroupsV2Api groupsV2Api;

    private final GroupAuthorizationProvider groupAuthorizationProvider;

    public GroupHelper(
            final ProfileKeyCredentialProvider profileKeyCredentialProvider,
            final ProfileProvider profileProvider,
            final SelfAddressProvider selfAddressProvider,
            final GroupsV2Operations groupsV2Operations,
            final GroupsV2Api groupsV2Api,
            final GroupAuthorizationProvider groupAuthorizationProvider
    ) {
        this.profileKeyCredentialProvider = profileKeyCredentialProvider;
        this.profileProvider = profileProvider;
        this.selfAddressProvider = selfAddressProvider;
        this.groupsV2Operations = groupsV2Operations;
        this.groupsV2Api = groupsV2Api;
        this.groupAuthorizationProvider = groupAuthorizationProvider;
    }

    public DecryptedGroup getDecryptedGroup(final GroupSecretParams groupSecretParams) {
        try {
            final GroupsV2AuthorizationString groupsV2AuthorizationString = groupAuthorizationProvider.getAuthorizationForToday(
                    groupSecretParams);
            return groupsV2Api.getGroup(groupSecretParams, groupsV2AuthorizationString);
        } catch (IOException | VerificationFailedException | InvalidGroupStateException e) {
            System.err.println("Failed to retrieve Group V2 info, ignoring ...");
            return null;
        }
    }

    public DecryptedGroupJoinInfo getDecryptedGroupJoinInfo(
            GroupMasterKey groupMasterKey, GroupLinkPassword password
    ) throws IOException, GroupLinkNotActiveException {
        GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

        return groupsV2Api.getGroupJoinInfo(groupSecretParams,
                Optional.fromNullable(password).transform(GroupLinkPassword::serialize),
                groupAuthorizationProvider.getAuthorizationForToday(groupSecretParams));
    }

    public GroupInfoV2 createGroupV2(
            String name, Collection<SignalServiceAddress> members, String avatarFile
    ) throws IOException {
        final byte[] avatarBytes = readAvatarBytes(avatarFile);
        final GroupsV2Operations.NewGroup newGroup = buildNewGroupV2(name, members, avatarBytes);
        if (newGroup == null) {
            return null;
        }

        final GroupSecretParams groupSecretParams = newGroup.getGroupSecretParams();

        final GroupsV2AuthorizationString groupAuthForToday;
        final DecryptedGroup decryptedGroup;
        try {
            groupAuthForToday = groupAuthorizationProvider.getAuthorizationForToday(groupSecretParams);
            groupsV2Api.putNewGroup(newGroup, groupAuthForToday);
            decryptedGroup = groupsV2Api.getGroup(groupSecretParams, groupAuthForToday);
        } catch (IOException | VerificationFailedException | InvalidGroupStateException e) {
            System.err.println("Failed to create V2 group: " + e.getMessage());
            return null;
        }
        if (decryptedGroup == null) {
            System.err.println("Failed to create V2 group!");
            return null;
        }

        final GroupIdV2 groupId = GroupUtils.getGroupIdV2(groupSecretParams);
        final GroupMasterKey masterKey = groupSecretParams.getMasterKey();
        GroupInfoV2 g = new GroupInfoV2(groupId, masterKey);
        g.setGroup(decryptedGroup);

        return g;
    }

    private byte[] readAvatarBytes(final String avatarFile) throws IOException {
        final byte[] avatarBytes;
        try (InputStream avatar = avatarFile == null ? null : new FileInputStream(avatarFile)) {
            avatarBytes = avatar == null ? null : IOUtils.readFully(avatar);
        }
        return avatarBytes;
    }

    private GroupsV2Operations.NewGroup buildNewGroupV2(
            String name, Collection<SignalServiceAddress> members, byte[] avatar
    ) {
        final ProfileKeyCredential profileKeyCredential = profileKeyCredentialProvider.getProfileKeyCredential(
                selfAddressProvider.getSelfAddress());
        if (profileKeyCredential == null) {
            System.err.println("Cannot create a V2 group as self does not have a versioned profile");
            return null;
        }

        if (!areMembersValid(members)) return null;

        GroupCandidate self = new GroupCandidate(selfAddressProvider.getSelfAddress().getUuid().orNull(),
                Optional.fromNullable(profileKeyCredential));
        Set<GroupCandidate> candidates = members.stream()
                .map(member -> new GroupCandidate(member.getUuid().get(),
                        Optional.fromNullable(profileKeyCredentialProvider.getProfileKeyCredential(member))))
                .collect(Collectors.toSet());

        final GroupSecretParams groupSecretParams = GroupSecretParams.generate();
        return groupsV2Operations.createNewGroup(groupSecretParams,
                name,
                Optional.fromNullable(avatar),
                self,
                candidates,
                Member.Role.DEFAULT,
                0);
    }

    private boolean areMembersValid(final Collection<SignalServiceAddress> members) {
        final int noUuidCapability = members.stream()
                .filter(address -> !address.getUuid().isPresent())
                .collect(Collectors.toUnmodifiableSet())
                .size();
        if (noUuidCapability > 0) {
            System.err.println("Cannot create a V2 group as " + noUuidCapability + " members don't have a UUID.");
            return false;
        }

        final int noGv2Capability = members.stream()
                .map(profileProvider::getProfile)
                .filter(profile -> profile != null && !profile.getCapabilities().gv2)
                .collect(Collectors.toUnmodifiableSet())
                .size();
        if (noGv2Capability > 0) {
            System.err.println("Cannot create a V2 group as " + noGv2Capability + " members don't support Groups V2.");
            return false;
        }

        return true;
    }

    public Pair<DecryptedGroup, GroupChange> updateGroupV2(
            GroupInfoV2 groupInfoV2, String name, String avatarFile
    ) throws IOException {
        final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(groupSecretParams);

        GroupChange.Actions.Builder change = name != null
                ? groupOperations.createModifyGroupTitle(name)
                : GroupChange.Actions.newBuilder();

        if (avatarFile != null) {
            final byte[] avatarBytes = readAvatarBytes(avatarFile);
            String avatarCdnKey = groupsV2Api.uploadAvatar(avatarBytes,
                    groupSecretParams,
                    groupAuthorizationProvider.getAuthorizationForToday(groupSecretParams));
            change.setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder().setAvatar(avatarCdnKey));
        }

        final Optional<UUID> uuid = this.selfAddressProvider.getSelfAddress().getUuid();
        if (uuid.isPresent()) {
            change.setSourceUuid(UuidUtil.toByteString(uuid.get()));
        }

        return commitChange(groupInfoV2, change);
    }

    public Pair<DecryptedGroup, GroupChange> updateGroupV2(
            GroupInfoV2 groupInfoV2, Set<SignalServiceAddress> newMembers
    ) throws IOException {
        final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(groupSecretParams);

        if (!areMembersValid(newMembers)) return null;

        Set<GroupCandidate> candidates = newMembers.stream()
                .map(member -> new GroupCandidate(member.getUuid().get(),
                        Optional.fromNullable(profileKeyCredentialProvider.getProfileKeyCredential(member))))
                .collect(Collectors.toSet());

        final GroupChange.Actions.Builder change = groupOperations.createModifyGroupMembershipChange(candidates,
                selfAddressProvider.getSelfAddress().getUuid().get());

        final Optional<UUID> uuid = this.selfAddressProvider.getSelfAddress().getUuid();
        if (uuid.isPresent()) {
            change.setSourceUuid(UuidUtil.toByteString(uuid.get()));
        }

        return commitChange(groupInfoV2, change);
    }

    public Pair<DecryptedGroup, GroupChange> leaveGroup(GroupInfoV2 groupInfoV2) throws IOException {
        List<DecryptedPendingMember> pendingMembersList = groupInfoV2.getGroup().getPendingMembersList();
        final UUID selfUuid = selfAddressProvider.getSelfAddress().getUuid().get();
        Optional<DecryptedPendingMember> selfPendingMember = DecryptedGroupUtil.findPendingByUuid(pendingMembersList,
                selfUuid);

        if (selfPendingMember.isPresent()) {
            return revokeInvites(groupInfoV2, Set.of(selfPendingMember.get()));
        } else {
            return ejectMembers(groupInfoV2, Set.of(selfUuid));
        }
    }

    public GroupChange joinGroup(
            GroupMasterKey groupMasterKey,
            GroupLinkPassword groupLinkPassword,
            DecryptedGroupJoinInfo decryptedGroupJoinInfo
    ) throws IOException {
        final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
        final GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(groupSecretParams);

        final SignalServiceAddress selfAddress = this.selfAddressProvider.getSelfAddress();
        final ProfileKeyCredential profileKeyCredential = profileKeyCredentialProvider.getProfileKeyCredential(
                selfAddress);
        if (profileKeyCredential == null) {
            throw new IOException("Cannot join a V2 group as self does not have a versioned profile");
        }

        boolean requestToJoin = decryptedGroupJoinInfo.getAddFromInviteLink()
                == AccessControl.AccessRequired.ADMINISTRATOR;
        GroupChange.Actions.Builder change = requestToJoin
                ? groupOperations.createGroupJoinRequest(profileKeyCredential)
                : groupOperations.createGroupJoinDirect(profileKeyCredential);

        change.setSourceUuid(UuidUtil.toByteString(selfAddress.getUuid().get()));

        return commitChange(groupSecretParams, decryptedGroupJoinInfo.getRevision(), change, groupLinkPassword);
    }

    public Pair<DecryptedGroup, GroupChange> acceptInvite(GroupInfoV2 groupInfoV2) throws IOException {
        final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        final GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(groupSecretParams);

        final SignalServiceAddress selfAddress = this.selfAddressProvider.getSelfAddress();
        final ProfileKeyCredential profileKeyCredential = profileKeyCredentialProvider.getProfileKeyCredential(
                selfAddress);
        if (profileKeyCredential == null) {
            throw new IOException("Cannot join a V2 group as self does not have a versioned profile");
        }

        final GroupChange.Actions.Builder change = groupOperations.createAcceptInviteChange(profileKeyCredential);

        final Optional<UUID> uuid = selfAddress.getUuid();
        if (uuid.isPresent()) {
            change.setSourceUuid(UuidUtil.toByteString(uuid.get()));
        }

        return commitChange(groupInfoV2, change);
    }

    public Pair<DecryptedGroup, GroupChange> revokeInvites(
            GroupInfoV2 groupInfoV2, Set<DecryptedPendingMember> pendingMembers
    ) throws IOException {
        final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        final GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(groupSecretParams);
        final Set<UuidCiphertext> uuidCipherTexts = pendingMembers.stream().map(member -> {
            try {
                return new UuidCiphertext(member.getUuidCipherText().toByteArray());
            } catch (InvalidInputException e) {
                throw new AssertionError(e);
            }
        }).collect(Collectors.toSet());
        return commitChange(groupInfoV2, groupOperations.createRemoveInvitationChange(uuidCipherTexts));
    }

    public Pair<DecryptedGroup, GroupChange> ejectMembers(GroupInfoV2 groupInfoV2, Set<UUID> uuids) throws IOException {
        final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        final GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(groupSecretParams);
        return commitChange(groupInfoV2, groupOperations.createRemoveMembersChange(uuids));
    }

    private Pair<DecryptedGroup, GroupChange> commitChange(
            GroupInfoV2 groupInfoV2, GroupChange.Actions.Builder change
    ) throws IOException {
        final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInfoV2.getMasterKey());
        final GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(groupSecretParams);
        final DecryptedGroup previousGroupState = groupInfoV2.getGroup();
        final int nextRevision = previousGroupState.getRevision() + 1;
        final GroupChange.Actions changeActions = change.setRevision(nextRevision).build();
        final DecryptedGroupChange decryptedChange;
        final DecryptedGroup decryptedGroupState;

        try {
            decryptedChange = groupOperations.decryptChange(changeActions,
                    selfAddressProvider.getSelfAddress().getUuid().get());
            decryptedGroupState = DecryptedGroupUtil.apply(previousGroupState, decryptedChange);
        } catch (VerificationFailedException | InvalidGroupStateException | NotAbleToApplyGroupV2ChangeException e) {
            throw new IOException(e);
        }

        GroupChange signedGroupChange = groupsV2Api.patchGroup(changeActions,
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
        final int nextRevision = currentRevision + 1;
        final GroupChange.Actions changeActions = change.setRevision(nextRevision).build();

        return groupsV2Api.patchGroup(changeActions,
                groupAuthorizationProvider.getAuthorizationForToday(groupSecretParams),
                Optional.fromNullable(password).transform(GroupLinkPassword::serialize));
    }

    public DecryptedGroup getUpdatedDecryptedGroup(
            DecryptedGroup group, byte[] signedGroupChange, GroupMasterKey groupMasterKey
    ) {
        try {
            final DecryptedGroupChange decryptedGroupChange = getDecryptedGroupChange(signedGroupChange,
                    groupMasterKey);
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
            GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(GroupSecretParams.deriveFromMasterKey(
                    groupMasterKey));

            try {
                return groupOperations.decryptChange(GroupChange.parseFrom(signedGroupChange), true).orNull();
            } catch (VerificationFailedException | InvalidGroupStateException | InvalidProtocolBufferException e) {
                return null;
            }
        }

        return null;
    }
}
