package org.asamk.signal.json;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;

record JsonGroupInfo(String groupId, String groupName, int revision, String type) {

    static JsonGroupInfo from(MessageEnvelope.Data.GroupContext groupContext, Manager m) {
        return new JsonGroupInfo(groupContext.groupId().toBase64(),
                m.getGroup(groupContext.groupId()).title(),
                groupContext.revision(),
                groupContext.isGroupUpdate() ? "UPDATE" : "DELIVER");
    }
}
