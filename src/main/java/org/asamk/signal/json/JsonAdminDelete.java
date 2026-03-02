package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@Schema(name = "AdminDelete")
public record JsonAdminDelete(
        @Schema(required = true) @Deprecated String targetAuthor,
        @Schema(required = true) String targetAuthorNumber,
        @Schema(required = true) String targetAuthorUuid,
        @Schema(required = true) long targetSentTimestamp
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

