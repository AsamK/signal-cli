package org.asamk.signal.json;

import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public record JsonContactAddress(
        SharedContact.PostalAddress.Type type,
        String label,
        String street,
        String pobox,
        String neighborhood,
        String city,
        String region,
        String postcode,
        String country
) {

    static JsonContactAddress from(SharedContact.PostalAddress address) {
        return new JsonContactAddress(address.getType(),
                Util.getStringIfNotBlank(address.getLabel()),
                Util.getStringIfNotBlank(address.getStreet()),
                Util.getStringIfNotBlank(address.getPobox()),
                Util.getStringIfNotBlank(address.getNeighborhood()),
                Util.getStringIfNotBlank(address.getCity()),
                Util.getStringIfNotBlank(address.getRegion()),
                Util.getStringIfNotBlank(address.getPostcode()),
                Util.getStringIfNotBlank(address.getCountry()));
    }
}
