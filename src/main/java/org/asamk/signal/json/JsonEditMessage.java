package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;

@JsonSchema(title = "EditMessage")
record JsonEditMessage(
        @JsonProperty(required = true) long targetSentTimestamp,
        @JsonProperty(required = true) JsonDataMessage dataMessage
) {

    static JsonEditMessage from(MessageEnvelope.Edit editMessage, Manager m) {
        return new JsonEditMessage(editMessage.targetSentTimestamp(),
                JsonDataMessage.from(editMessage.dataMessage(), m));
    }
}
