package org.asamk.signal.json;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;

import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(title = "GroupInfo")
record JsonGroupInfo(String groupId, String groupName, int revision, String type) {

    static JsonGroupInfo from(MessageEnvelope.Data.GroupContext groupContext, Manager m) {
        final var group = m.getGroup(groupContext.groupId());
        return new JsonGroupInfo(groupContext.groupId().toBase64(),
                group == null ? null : group.title(),
                groupContext.revision(),
                groupContext.isGroupUpdate() ? "UPDATE" : "DELIVER");
    }
}
