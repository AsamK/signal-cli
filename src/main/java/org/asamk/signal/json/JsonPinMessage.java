package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@Schema(name = "PinMessage")
public record JsonPinMessage(
        @Deprecated String targetAuthor,
        @Schema(required = true) String targetAuthorNumber,
        @Schema(required = true) String targetAuthorUuid,
        @Schema(required = true) long targetSentTimestamp,
        @Schema(required = true) long pinDurationSeconds
) {

    static JsonPinMessage from(MessageEnvelope.Data.PinMessage pinMessage) {
        final var address = pinMessage.targetAuthor();
        final var targetAuthor = address.getLegacyIdentifier();
        final var targetAuthorNumber = address.number().orElse(null);
        final var targetAuthorUuid = address.uuid().map(UUID::toString).orElse(null);
        final var targetSentTimestamp = pinMessage.targetSentTimestamp();
        final var pinDurationSeconds = pinMessage.pinDurationSeconds();

        return new JsonPinMessage(targetAuthor,
                targetAuthorNumber,
                targetAuthorUuid,
                targetSentTimestamp,
                pinDurationSeconds);
    }
}

