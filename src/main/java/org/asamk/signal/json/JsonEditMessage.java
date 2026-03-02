package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;

@Schema(name = "EditMessage")
record JsonEditMessage(
        @Schema(required = true) long targetSentTimestamp,
        @Schema(required = true) JsonDataMessage dataMessage
) {

    static JsonEditMessage from(MessageEnvelope.Edit editMessage, Manager m) {
        return new JsonEditMessage(editMessage.targetSentTimestamp(),
                JsonDataMessage.from(editMessage.dataMessage(), m));
    }
}
