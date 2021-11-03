package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

public record JsonContactAddress(
        String type,
        String label,
        String street,
        String pobox,
        String neighborhood,
        String city,
        String region,
        String postcode,
        String country
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
