package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        label = address.getLabel().orNull();
        street = address.getStreet().orNull();
        pobox = address.getPobox().orNull();
        neighborhood = address.getNeighborhood().orNull();
        city = address.getCity().orNull();
        region = address.getRegion().orNull();
        postcode = address.getPostcode().orNull();
        country = address.getCountry().orNull();
    }
}
