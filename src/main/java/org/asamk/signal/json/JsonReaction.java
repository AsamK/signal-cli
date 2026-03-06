package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@JsonSchema(title = "Reaction")
public record JsonReaction(
        @JsonProperty(required = true) String emoji,
        @Deprecated String targetAuthor,
        @JsonProperty(required = true) String targetAuthorNumber,
        @JsonProperty(required = true) String targetAuthorUuid,
        @JsonProperty(required = true) long targetSentTimestamp,
        @JsonProperty(required = true) boolean isRemove
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
