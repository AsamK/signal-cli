package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

@JsonSchema(title = "ContactEmail")
public record JsonContactEmail(
        @JsonProperty(required = true) String value,
        @JsonProperty(required = true) String type,
        @JsonProperty(required = true) String label
) {

    static JsonContactEmail from(MessageEnvelope.Data.SharedContact.Email email) {
        return new JsonContactEmail(email.value(), email.type().name(), Util.getStringIfNotBlank(email.label()));
    }
}
