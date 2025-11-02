package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;

public record JsonPollCreate(
        String question, boolean allowMultiple, List<String> options
) {

    static JsonPollCreate from(MessageEnvelope.Data.PollCreate pollCreate) {
        final var question = pollCreate.question();
        final var allowMultiple = pollCreate.allowMultiple();
        final var options = pollCreate.options();

        return new JsonPollCreate(question, allowMultiple, options);
    }
}
