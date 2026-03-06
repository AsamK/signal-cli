package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

@JsonSchema(title = "ContactName")
public record JsonContactName(
        @JsonProperty(required = true) String nickname,
        @JsonProperty(required = true) String given,
        @JsonProperty(required = true) String family,
        @JsonProperty(required = true) String prefix,
        @JsonProperty(required = true) String suffix,
        @JsonProperty(required = true) String middle
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
