package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.internal.util.Base64;

import java.util.List;

class JsonGroupInfo {
    String groupId;
    List<String> members;
    String name;
    String type;

    JsonGroupInfo(SignalServiceGroup groupInfo) {
        this.groupId = Base64.encodeBytes(groupInfo.getGroupId());
        if (groupInfo.getMembers().isPresent()) {
            this.members = groupInfo.getMembers().get();
        }
        if (groupInfo.getName().isPresent()) {
            this.name = groupInfo.getName().get();
        }
        this.type = groupInfo.getType().toString();
    }
}
