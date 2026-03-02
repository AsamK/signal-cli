package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@Schema(name = "UnpinMessage")
public record JsonUnpinMessage(
        @Schema(required = true) @Deprecated String targetAuthor,
        @Schema(required = true) String targetAuthorNumber,
        @Schema(required = true) String targetAuthorUuid,
        @Schema(required = true) long targetSentTimestamp
) {

    static JsonUnpinMessage from(MessageEnvelope.Data.UnpinMessage unpinMessage) {
        final var address = unpinMessage.targetAuthor();
        final var targetAuthor = address.getLegacyIdentifier();
        final var targetAuthorNumber = address.number().orElse(null);
        final var targetAuthorUuid = address.uuid().map(UUID::toString).orElse(null);
        final var targetSentTimestamp = unpinMessage.targetSentTimestamp();

        return new JsonUnpinMessage(targetAuthor, targetAuthorNumber, targetAuthorUuid, targetSentTimestamp);
    }
}

