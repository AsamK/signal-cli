package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class JsonContactAddress {

    @JsonProperty
    private final SharedContact.PostalAddress.Type type;

    @JsonProperty
    private final String label;

    @JsonProperty
    private final String street;

    @JsonProperty
    private final String pobox;

    @JsonProperty
    private final String neighborhood;

    @JsonProperty
    private final String city;

    @JsonProperty
    private final String region;

    @JsonProperty
    private final String postcode;

    @JsonProperty
    private final String country;

    public JsonContactAddress(SharedContact.PostalAddress address) {
        type = address.getType();
        label = Util.getStringIfNotBlank(address.getLabel());
        street = Util.getStringIfNotBlank(address.getStreet());
        pobox = Util.getStringIfNotBlank(address.getPobox());
        neighborhood = Util.getStringIfNotBlank(address.getNeighborhood());
        city = Util.getStringIfNotBlank(address.getCity());
        region = Util.getStringIfNotBlank(address.getRegion());
        postcode = Util.getStringIfNotBlank(address.getPostcode());
        country = Util.getStringIfNotBlank(address.getCountry());
    }
}
