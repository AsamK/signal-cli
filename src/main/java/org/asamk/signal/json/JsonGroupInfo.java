package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

record JsonGroupInfo(String groupId, String type) {

    static JsonGroupInfo from(MessageEnvelope.Data.GroupContext groupContext) {
        return new JsonGroupInfo(groupContext.groupId().toBase64(),
                groupContext.isGroupUpdate() ? "UPDATE" : "DELIVER");
    }
}
