package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@Schema(name = "UnpinMessage")
public record JsonUnpinMessage(
        @Deprecated String targetAuthor, String targetAuthorNumber, String targetAuthorUuid, long targetSentTimestamp
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

