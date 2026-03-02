package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

@Schema(name = "ContactEmail")
public record JsonContactEmail(
        @Schema(required = true) String value,
        @Schema(required = true) String type,
        @Schema(required = true) String label
) {

    static JsonContactEmail from(MessageEnvelope.Data.SharedContact.Email email) {
        return new JsonContactEmail(email.value(), email.type().name(), Util.getStringIfNotBlank(email.label()));
    }
}
