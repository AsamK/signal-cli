package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

public record JsonPollTerminate(long targetSentTimestamp) {

    static JsonPollTerminate from(MessageEnvelope.Data.PollTerminate pollTerminate) {
        final var targetSentTimestamp = pollTerminate.targetSentTimestamp();

        return new JsonPollTerminate(targetSentTimestamp);
    }
}
