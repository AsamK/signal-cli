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

    private String getValueIfActuallyPopulated(String string) {
        if (string == null || string.isBlank()) {
            return null;
        }
        return string;
    }

    public JsonContactAddress(SharedContact.PostalAddress address) {
        type = address.getType();
        label = getValueIfActuallyPopulated(address.getLabel().orNull());
        street = getValueIfActuallyPopulated(address.getStreet().orNull());
        pobox = getValueIfActuallyPopulated(address.getPobox().orNull());
        neighborhood = getValueIfActuallyPopulated(address.getNeighborhood().orNull());
        city = getValueIfActuallyPopulated(address.getCity().orNull());
        region = getValueIfActuallyPopulated(address.getRegion().orNull());
        postcode = getValueIfActuallyPopulated(address.getPostcode().orNull());
        country = getValueIfActuallyPopulated(address.getCountry().orNull());
        if (country == null) {
            System.out.println("Is null");
        } else {
            System.out.println("Present: " + country);
        }
    }
}
