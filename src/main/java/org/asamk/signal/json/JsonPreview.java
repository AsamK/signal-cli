package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

@Schema(name = "Preview")
public record JsonPreview(
        @Schema(required = true) String url,
        @Schema(required = true) String title,
        @Schema(required = true) String description,
        @Schema(required = true) JsonAttachment image
) {

    static JsonPreview from(MessageEnvelope.Data.Preview preview) {
        return new JsonPreview(preview.url(),
                preview.title(),
                preview.description(),
                preview.image().map(JsonAttachment::from).orElse(null));
    }
}
