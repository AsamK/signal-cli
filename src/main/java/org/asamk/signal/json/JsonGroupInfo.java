package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.Base64;

record JsonGroupInfo(String groupId, String type) {

    static JsonGroupInfo from(MessageEnvelope.Data.GroupContext groupContext) {
        return new JsonGroupInfo(groupContext.groupId().toBase64(),
                groupContext.isGroupUpdate() ? "UPDATE" : "DELIVER");
    }

    static JsonGroupInfo from(byte[] groupId) {
        return new JsonGroupInfo(Base64.getEncoder().encodeToString(groupId), "DELIVER");
    }
}
