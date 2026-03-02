package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@Schema(name = "Reaction")
public record JsonReaction(
        @Schema(required = true) String emoji,
        @Schema(required = true) @Deprecated String targetAuthor,
        @Schema(required = true) String targetAuthorNumber,
        @Schema(required = true) String targetAuthorUuid,
        @Schema(required = true) long targetSentTimestamp,
        @Schema(required = true) boolean isRemove
) {

    static JsonReaction from(MessageEnvelope.Data.Reaction reaction) {
        final var emoji = reaction.emoji();
        final var address = reaction.targetAuthor();
        final var targetAuthor = address.getLegacyIdentifier();
        final var targetAuthorNumber = address.number().orElse(null);
        final var targetAuthorUuid = address.uuid().map(UUID::toString).orElse(null);
        final var targetSentTimestamp = reaction.targetSentTimestamp();
        final var isRemove = reaction.isRemove();
        return new JsonReaction(emoji,
                targetAuthor,
                targetAuthorNumber,
                targetAuthorUuid,
                targetSentTimestamp,
                isRemove);
    }
}
