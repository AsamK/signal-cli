package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.RecipientAddress;

import java.util.UUID;

@Schema(name = "RecipientAddress")
public record JsonRecipientAddress(
        @Schema(required = true) String uuid,
        @Schema(required = true) String number,
        @Schema(required = true) String username
) {

    public static JsonRecipientAddress from(RecipientAddress address) {
        return new JsonRecipientAddress(address.uuid().map(UUID::toString).orElse(null),
                address.number().orElse(null),
                address.username().orElse(null));
    }
}
