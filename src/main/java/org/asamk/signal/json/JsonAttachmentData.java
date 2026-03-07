package org.asamk.signal.json;

import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(title = "AttachmentData")
public record JsonAttachmentData(
        String data
) {}
