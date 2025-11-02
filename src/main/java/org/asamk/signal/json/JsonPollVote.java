package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;
import java.util.UUID;

public record JsonPollVote(
        @Deprecated String author,
        String authorNumber,
        String authorUuid,
        long targetSentTimestamp,
        List<Integer> optionIndexes,
        int voteCount
) {

    static JsonPollVote from(MessageEnvelope.Data.PollVote pollVote) {
        final var address = pollVote.targetAuthor();
        final var author = address.getLegacyIdentifier();
        final var authorNumber = address.number().orElse(null);
        final var authorUuid = address.uuid().map(UUID::toString).orElse(null);
        final var targetSentTimestamp = pollVote.targetSentTimestamp();
        final var optionIndexes = pollVote.optionIndexes();
        final var voteCount = pollVote.voteCount();

        return new JsonPollVote(author, authorNumber, authorUuid, targetSentTimestamp, optionIndexes, voteCount);
    }
}
