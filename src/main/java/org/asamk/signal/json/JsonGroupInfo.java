package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;

@Schema(name = "GroupInfo")
record JsonGroupInfo(
        @Schema(required = true) String groupId,
        @Schema(required = true) String groupName,
        @Schema(required = true) int revision,
        @Schema(required = true) String type
) {

    static JsonGroupInfo from(MessageEnvelope.Data.GroupContext groupContext, Manager m) {
        return new JsonGroupInfo(groupContext.groupId().toBase64(),
                m.getGroup(groupContext.groupId()).title(),
                groupContext.revision(),
                groupContext.isGroupUpdate() ? "UPDATE" : "DELIVER");
    }
}
