package org.asamk.signal.manager;

import org.asamk.signal.storage.groups.GroupInfo;
import org.asamk.signal.storage.groups.GroupInfoV1;
import org.asamk.signal.storage.groups.GroupInfoV2;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;

public class GroupUtils {

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
}
