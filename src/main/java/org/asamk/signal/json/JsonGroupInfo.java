package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;

@JsonSchema(title = "GroupInfo")
record JsonGroupInfo(
        @JsonProperty(required = true) String groupId,
        @JsonProperty(required = true) String groupName,
        @JsonProperty(required = true) int revision,
        @JsonProperty(required = true) String type
) {

    static JsonGroupInfo from(MessageEnvelope.Data.GroupContext groupContext, Manager m) {
        return new JsonGroupInfo(groupContext.groupId().toBase64(),
                m.getGroup(groupContext.groupId()).title(),
                groupContext.revision(),
                groupContext.isGroupUpdate() ? "UPDATE" : "DELIVER");
    }
}
