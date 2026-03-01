package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import org.asamk.signal.manager.api.MessageEnvelope;

@Schema(name = "QuotedAttachment")
public record JsonQuotedAttachment(
        String contentType, String filename, @JsonInclude(JsonInclude.Include.NON_NULL) JsonAttachment thumbnail
) {

    static JsonQuotedAttachment from(MessageEnvelope.Data.Attachment quotedAttachment) {
        final var contentType = quotedAttachment.contentType();
        final var filename = quotedAttachment.fileName().orElse(null);
        final var thumbnail = quotedAttachment.thumbnail().isPresent()
                ? JsonAttachment.from(quotedAttachment.thumbnail().get())
                : null;
        return new JsonQuotedAttachment(contentType, filename, thumbnail);
    }
}
