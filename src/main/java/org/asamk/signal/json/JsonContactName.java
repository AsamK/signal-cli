package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

@Schema(name = "ContactName")
public record JsonContactName(
        @Schema(required = true) String nickname,
        @Schema(required = true) String given,
        @Schema(required = true) String family,
        @Schema(required = true) String prefix,
        @Schema(required = true) String suffix,
        @Schema(required = true) String middle
) {

    static JsonContactName from(MessageEnvelope.Data.SharedContact.Name name) {
        return new JsonContactName(Util.getStringIfNotBlank(name.nickname()),
                Util.getStringIfNotBlank(name.given()),
                Util.getStringIfNotBlank(name.family()),
                Util.getStringIfNotBlank(name.prefix()),
                Util.getStringIfNotBlank(name.suffix()),
                Util.getStringIfNotBlank(name.middle()));
    }
}
