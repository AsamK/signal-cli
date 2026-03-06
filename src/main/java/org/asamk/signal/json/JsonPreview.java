package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

@JsonSchema(title = "Preview")
public record JsonPreview(
        @JsonProperty(required = true) String url,
        @JsonProperty(required = true) String title,
        @JsonProperty(required = true) String description,
        @JsonProperty(required = true) JsonAttachment image
) {

    static JsonPreview from(MessageEnvelope.Data.Preview preview) {
        return new JsonPreview(preview.url(),
                preview.title(),
                preview.description(),
                preview.image().map(JsonAttachment::from).orElse(null));
    }
}
