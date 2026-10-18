package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(title = "AdminDelete")
public record JsonAdminDelete(
        @Deprecated String targetAuthor,
        String targetAuthorNumber,
        String targetAuthorUuid,
        long targetTimestamp,
        @Deprecated long targetSentTimestamp
) {

    static JsonAdminDelete from(MessageEnvelope.Data.AdminDelete adminDelete) {
        final var address = adminDelete.targetAuthor();
        final var targetAuthor = address.getLegacyIdentifier();
        final var targetAuthorNumber = address.number().orElse(null);
        final var targetAuthorUuid = address.uuid().map(UUID::toString).orElse(null);
        final var targetTimestamp = adminDelete.targetSentTimestamp();

        return new JsonAdminDelete(targetAuthor,
                targetAuthorNumber,
                targetAuthorUuid,
                targetTimestamp,
                targetTimestamp);
    }
}

