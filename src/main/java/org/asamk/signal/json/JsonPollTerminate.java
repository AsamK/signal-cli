package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

@JsonSchema(title = "PollTerminate")
public record JsonPollTerminate(@JsonProperty(required = true) long targetSentTimestamp) {

    static JsonPollTerminate from(MessageEnvelope.Data.PollTerminate pollTerminate) {
        final var targetSentTimestamp = pollTerminate.targetSentTimestamp();

        return new JsonPollTerminate(targetSentTimestamp);
    }
}
