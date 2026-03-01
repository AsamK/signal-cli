package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@Schema(name = "AdminDelete")
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

