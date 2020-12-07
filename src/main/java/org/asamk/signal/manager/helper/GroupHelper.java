package org.asamk.signal.manager.helper;

import org.asamk.signal.storage.groups.GroupInfo;
import org.asamk.signal.storage.groups.GroupInfoV1;
import org.asamk.signal.storage.groups.GroupInfoV2;
import org.signal.storageservice.protos.groups.Member;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class GroupHelper {

    private final ProfileKeyCredentialProvider profileKeyCredentialProvider;

    private final ProfileProvider profileProvider;

    private final SelfAddressProvider selfAddressProvider;

    private final GroupsV2Operations groupsV2Operations;

    public GroupHelper(
            final ProfileKeyCredentialProvider profileKeyCredentialProvider,
            final ProfileProvider profileProvider,
            final SelfAddressProvider selfAddressProvider,
            final GroupsV2Operations groupsV2Operations
    ) {
        this.profileKeyCredentialProvider = profileKeyCredentialProvider;
        this.profileProvider = profileProvider;
        this.selfAddressProvider = selfAddressProvider;
        this.groupsV2Operations = groupsV2Operations;
    }

    public static void setGroupContext(
            final SignalServiceDataMessage.Builder messageBuilder, final GroupInfo groupInfo
    ) {
        if (groupInfo instanceof GroupInfoV1) {
            SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.DELIVER)
                    .withId(groupInfo.groupId)
                    .build();
            messageBuilder.asGroupMessage(group);
        } else {
            final GroupInfoV2 groupInfoV2 = (GroupInfoV2) groupInfo;
            SignalServiceGroupV2 group = SignalServiceGroupV2.newBuilder(groupInfoV2.getMasterKey())
                    .withRevision(groupInfoV2.getGroup() == null ? 0 : groupInfoV2.getGroup().getRevision())
                    .build();
            messageBuilder.asGroupMessage(group);
        }
    }

    public GroupsV2Operations.NewGroup createGroupV2(
            String name, Collection<SignalServiceAddress> members, byte[] avatar
    ) {
        final ProfileKeyCredential profileKeyCredential = profileKeyCredentialProvider.getProfileKeyCredential(
                selfAddressProvider.getSelfAddress());
        if (profileKeyCredential == null) {
            System.err.println("Cannot create a V2 group as self does not have a versioned profile");
            return null;
        }

        final int noUuidCapability = members.stream()
                .filter(address -> !address.getUuid().isPresent())
                .collect(Collectors.toUnmodifiableSet())
                .size();
        if (noUuidCapability > 0) {
            System.err.println("Cannot create a V2 group as " + noUuidCapability + " members don't have a UUID.");
            return null;
        }

        final int noGv2Capability = members.stream()
                .map(profileProvider::getProfile)
                .filter(profile -> !profile.getCapabilities().gv2)
                .collect(Collectors.toUnmodifiableSet())
                .size();
        if (noGv2Capability > 0) {
            System.err.println("Cannot create a V2 group as " + noGv2Capability + " members don't support Groups V2.");
            return null;
        }

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

}
