package org.asamk.signal.json;

import org.asamk.signal.manager.GroupUtils;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.util.ArrayList;
import java.util.List;

class JsonGroupInfo {

    String groupId;
    List<String> members;
    String name;
    String type;

    JsonGroupInfo(SignalServiceGroup groupInfo) {
        this.groupId = Base64.encodeBytes(groupInfo.getGroupId());
        if (groupInfo.getMembers().isPresent()) {
            this.members = new ArrayList<>(groupInfo.getMembers().get().size());
            for (SignalServiceAddress address : groupInfo.getMembers().get()) {
                this.members.add(address.getLegacyIdentifier());
            }
        }
        if (groupInfo.getName().isPresent()) {
            this.name = groupInfo.getName().get();
        }
        this.type = groupInfo.getType().toString();
    }

    JsonGroupInfo(SignalServiceGroupV2 groupInfo) {
        this.groupId = GroupUtils.getGroupIdV2(groupInfo.getMasterKey()).toBase64();
        this.type = groupInfo.hasSignedGroupChange() ? "UPDATE" : "DELIVER";
    }

    JsonGroupInfo(byte[] groupId) {
        this.groupId = Base64.encodeBytes(groupId);
    }
}
