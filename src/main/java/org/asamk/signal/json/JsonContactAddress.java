package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

@Schema(name = "ContactAddress")
public record JsonContactAddress(
        @Schema(required = true) String type,
        @Schema(required = true) String label,
        @Schema(required = true) String street,
        @Schema(required = true) String pobox,
        @Schema(required = true) String neighborhood,
        @Schema(required = true) String city,
        @Schema(required = true) String region,
        @Schema(required = true) String postcode,
        @Schema(required = true) String country
) {

    static JsonContactAddress from(MessageEnvelope.Data.SharedContact.Address address) {
        return new JsonContactAddress(address.type().name(),
                Util.getStringIfNotBlank(address.label()),
                Util.getStringIfNotBlank(address.street()),
                Util.getStringIfNotBlank(address.pobox()),
                Util.getStringIfNotBlank(address.neighborhood()),
                Util.getStringIfNotBlank(address.city()),
                Util.getStringIfNotBlank(address.region()),
                Util.getStringIfNotBlank(address.postcode()),
                Util.getStringIfNotBlank(address.country()));
    }
}
