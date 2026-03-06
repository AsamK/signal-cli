package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

@JsonSchema(title = "ContactAddress")
public record JsonContactAddress(
        @JsonProperty(required = true) String type,
        @JsonProperty(required = true) String label,
        @JsonProperty(required = true) String street,
        @JsonProperty(required = true) String pobox,
        @JsonProperty(required = true) String neighborhood,
        @JsonProperty(required = true) String city,
        @JsonProperty(required = true) String region,
        @JsonProperty(required = true) String postcode,
        @JsonProperty(required = true) String country
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
