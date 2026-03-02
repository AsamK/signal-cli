package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;

@Schema(name = "PollCreate")
public record JsonPollCreate(
        @Schema(required = true) String question,
        @Schema(required = true) boolean allowMultiple,
        @Schema(required = true) List<String> options
) {

    static JsonPollCreate from(MessageEnvelope.Data.PollCreate pollCreate) {
        final var question = pollCreate.question();
        final var allowMultiple = pollCreate.allowMultiple();
        final var options = pollCreate.options();

        return new JsonPollCreate(question, allowMultiple, options);
    }
}
