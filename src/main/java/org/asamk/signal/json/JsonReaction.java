package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

public record JsonReaction(
        String emoji,
        @Deprecated String targetAuthor,
        String targetAuthorNumber,
        String targetAuthorUuid,
        long targetSentTimestamp,
        boolean isRemove
) {

    static JsonReaction from(MessageEnvelope.Data.Reaction reaction) {
        final var emoji = reaction.emoji();
        final var address = reaction.targetAuthor();
        final var targetAuthor = address.getLegacyIdentifier();
        final var targetAuthorNumber = address.getNumber().orElse(null);
        final var targetAuthorUuid = address.getUuid().map(UUID::toString).orElse(null);
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
