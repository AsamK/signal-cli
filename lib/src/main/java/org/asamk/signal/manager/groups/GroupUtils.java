package org.asamk.signal.manager.groups;

import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.kdf.HKDFv3;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;

public class GroupUtils {

    public static void setGroupContext(
            final SignalServiceDataMessage.Builder messageBuilder, final GroupInfo groupInfo
    ) {
        if (groupInfo instanceof GroupInfoV1) {
            var group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.DELIVER)
                    .withId(groupInfo.getGroupId().serialize())
                    .build();
            messageBuilder.asGroupMessage(group);
        } else {
            final var groupInfoV2 = (GroupInfoV2) groupInfo;
            var group = SignalServiceGroupV2.newBuilder(groupInfoV2.getMasterKey())
                    .withRevision(groupInfoV2.getGroup() == null ? 0 : groupInfoV2.getGroup().getRevision())
                    .build();
            messageBuilder.asGroupMessage(group);
        }
    }

    public static GroupId getGroupId(SignalServiceGroupContext context) {
        if (context.getGroupV1().isPresent()) {
            return GroupId.v1(context.getGroupV1().get().getGroupId());
        } else if (context.getGroupV2().isPresent()) {
            return getGroupIdV2(context.getGroupV2().get().getMasterKey());
        } else {
            return null;
        }
    }

    public static GroupIdV2 getGroupIdV2(GroupSecretParams groupSecretParams) {
        return GroupId.v2(groupSecretParams.getPublicParams().getGroupIdentifier().serialize());
    }

    public static GroupIdV2 getGroupIdV2(GroupMasterKey groupMasterKey) {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
        return getGroupIdV2(groupSecretParams);
    }

    public static GroupIdV2 getGroupIdV2(GroupIdV1 groupIdV1) {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(deriveV2MigrationMasterKey(groupIdV1));
        return getGroupIdV2(groupSecretParams);
    }

    private static GroupMasterKey deriveV2MigrationMasterKey(GroupIdV1 groupIdV1) {
        try {
            return new GroupMasterKey(new HKDFv3().deriveSecrets(groupIdV1.serialize(),
                    "GV2 Migration".getBytes(),
                    GroupMasterKey.SIZE));
        } catch (InvalidInputException e) {
            throw new AssertionError(e);
        }
    }
}
