package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;


@JsonSchema(title = "AttachmentData")
public record JsonAttachmentData(
        @JsonProperty(required = true) String data
) {}
