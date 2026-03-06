package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@JsonSchema(title = "UnpinMessage")
public record JsonUnpinMessage(
        @Deprecated String targetAuthor,
        @JsonProperty(required = true) String targetAuthorNumber,
        @JsonProperty(required = true) String targetAuthorUuid,
        @JsonProperty(required = true) long targetSentTimestamp
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

