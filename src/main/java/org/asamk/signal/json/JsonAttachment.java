package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

@JsonSchema(title = "Attachment")
record JsonAttachment(
        @JsonProperty(required = true) String contentType,
        @JsonProperty(required = true) String filename,
        @JsonProperty(required = true) String id,
        @JsonProperty(required = true) Long size,
        @JsonProperty(required = true) Integer width,
        @JsonProperty(required = true) Integer height,
        @JsonProperty(required = true) String caption,
        @JsonProperty(required = true) Long uploadTimestamp
) {

    static JsonAttachment from(MessageEnvelope.Data.Attachment attachment) {
        final var id = attachment.id().orElse(null);
        final var filename = attachment.fileName().orElse(null);
        final var size = attachment.size().orElse(null);
        final var width = attachment.width().orElse(null);
        final var height = attachment.height().orElse(null);
        final var caption = attachment.caption().orElse(null);
        final var uploadTimestamp = attachment.uploadTimestamp().orElse(null);

        return new JsonAttachment(attachment.contentType(),
                filename,
                id,
                size,
                width,
                height,
                caption,
                uploadTimestamp);
    }
}
