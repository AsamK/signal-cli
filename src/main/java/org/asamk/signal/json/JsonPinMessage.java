package org.asamk.signal.json;

import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@JsonSchema(title = "PinMessage")
public record JsonPinMessage(
        @Deprecated String targetAuthor,
        String targetAuthorNumber,
        String targetAuthorUuid,
        long targetSentTimestamp,
        long pinDurationSeconds
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

