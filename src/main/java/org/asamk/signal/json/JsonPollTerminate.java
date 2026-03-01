package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

@Schema(name = "PollTerminate")
public record JsonPollTerminate(long targetSentTimestamp) {

    static JsonPollTerminate from(MessageEnvelope.Data.PollTerminate pollTerminate) {
        final var targetSentTimestamp = pollTerminate.targetSentTimestamp();

        return new JsonPollTerminate(targetSentTimestamp);
    }
}
