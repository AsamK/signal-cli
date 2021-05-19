package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

class JsonGroupInfo {

    @JsonProperty
    final String groupId;

    @JsonProperty
    final String type;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final String name;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<String> members;

    JsonGroupInfo(SignalServiceGroup groupInfo) {
        this.groupId = Base64.getEncoder().encodeToString(groupInfo.getGroupId());
        this.type = groupInfo.getType().toString();
        this.name = groupInfo.getName().orNull();
        if (groupInfo.getMembers().isPresent()) {
            this.members = groupInfo.getMembers()
                    .get()
                    .stream()
                    .map(Util::getLegacyIdentifier)
                    .collect(Collectors.toList());
        } else {
            this.members = null;
        }
    }

    JsonGroupInfo(SignalServiceGroupV2 groupInfo) {
        this.groupId = GroupUtils.getGroupIdV2(groupInfo.getMasterKey()).toBase64();
        this.type = groupInfo.hasSignedGroupChange() ? "UPDATE" : "DELIVER";
        this.members = null;
        this.name = null;
    }

    JsonGroupInfo(byte[] groupId) {
        this.groupId = Base64.getEncoder().encodeToString(groupId);
        this.type = "DELIVER";
        this.members = null;
        this.name = null;
    }
}
