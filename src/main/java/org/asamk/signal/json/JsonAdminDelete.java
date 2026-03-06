package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@JsonSchema(title = "AdminDelete")
public record JsonAdminDelete(
        @Deprecated String targetAuthor,
        @JsonProperty(required = true) String targetAuthorNumber,
        @JsonProperty(required = true) String targetAuthorUuid,
        @JsonProperty(required = true) long targetSentTimestamp
) {

    static JsonAdminDelete from(MessageEnvelope.Data.AdminDelete adminDelete) {
        final var address = adminDelete.targetAuthor();
        final var targetAuthor = address.getLegacyIdentifier();
        final var targetAuthorNumber = address.number().orElse(null);
        final var targetAuthorUuid = address.uuid().map(UUID::toString).orElse(null);
        final var targetSentTimestamp = adminDelete.targetSentTimestamp();

        return new JsonAdminDelete(targetAuthor, targetAuthorNumber, targetAuthorUuid, targetSentTimestamp);
    }
}

