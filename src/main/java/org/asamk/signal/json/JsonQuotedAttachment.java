package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public record JsonQuotedAttachment(
        String contentType, String filename, @JsonInclude(JsonInclude.Include.NON_NULL) JsonAttachment thumbnail
) {

    static JsonQuotedAttachment from(SignalServiceDataMessage.Quote.QuotedAttachment quotedAttachment) {
        final var contentType = quotedAttachment.getContentType();
        final var filename = quotedAttachment.getFileName();
        final JsonAttachment thumbnail;
        if (quotedAttachment.getThumbnail() != null) {
            thumbnail = JsonAttachment.from(quotedAttachment.getThumbnail());
        } else {
            thumbnail = null;
        }
        return new JsonQuotedAttachment(contentType, filename, thumbnail);
    }
}
