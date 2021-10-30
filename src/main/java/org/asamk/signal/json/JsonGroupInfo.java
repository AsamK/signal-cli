package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

record JsonGroupInfo(
        String groupId,
        String type,
        @JsonInclude(JsonInclude.Include.NON_NULL) String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> members
) {

    static JsonGroupInfo from(SignalServiceGroup groupInfo) {
        return new JsonGroupInfo(Base64.getEncoder().encodeToString(groupInfo.getGroupId()),
                groupInfo.getType().toString(),
                groupInfo.getName().orNull(),
                groupInfo.getMembers().isPresent() ? groupInfo.getMembers()
                        .get()
                        .stream()
                        .map(Util::getLegacyIdentifier)
                        .collect(Collectors.toList()) : null);
    }

    static JsonGroupInfo from(SignalServiceGroupV2 groupInfo) {
        return new JsonGroupInfo(GroupUtils.getGroupIdV2(groupInfo.getMasterKey()).toBase64(),
                groupInfo.hasSignedGroupChange() ? "UPDATE" : "DELIVER",
                null,
                null);
    }

    static JsonGroupInfo from(byte[] groupId) {
        return new JsonGroupInfo(Base64.getEncoder().encodeToString(groupId), "DELIVER", null, null);
    }
}
