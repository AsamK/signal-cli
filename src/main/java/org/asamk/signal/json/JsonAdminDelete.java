package org.asamk.signal.json;

import io.micronaut.jsonschema.JsonSchema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@JsonSchema(title = "AdminDelete")
public record JsonAdminDelete(
        @Deprecated String targetAuthor, String targetAuthorNumber, String targetAuthorUuid, long targetSentTimestamp
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

