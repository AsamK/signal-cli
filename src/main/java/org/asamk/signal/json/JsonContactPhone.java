package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

@Schema(name = "ContactPhone")
public record JsonContactPhone(
        @Schema(required = true) String value,
        @Schema(required = true) String type,
        @Schema(required = true) String label
) {

    static JsonContactPhone from(MessageEnvelope.Data.SharedContact.Phone phone) {
        return new JsonContactPhone(phone.value(), phone.type().name(), Util.getStringIfNotBlank(phone.label()));
    }
}
