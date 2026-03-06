package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;
import java.util.UUID;

@Schema(name = "PollVote")
public record JsonPollVote(
        @Deprecated String author,
        @Schema(required = true) String authorNumber,
        @Schema(required = true) String authorUuid,
        @Schema(required = true) long targetSentTimestamp,
        @Schema(required = true) List<Integer> optionIndexes,
        @Schema(required = true) int voteCount
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
