package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public class JsonQuotedAttachment {

    @JsonProperty
    final String contentType;

    @JsonProperty
    final String filename;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonAttachment thumbnail;

    JsonQuotedAttachment(SignalServiceDataMessage.Quote.QuotedAttachment quotedAttachment) {
        contentType = quotedAttachment.getContentType();
        filename = quotedAttachment.getFileName();
        if (quotedAttachment.getThumbnail() != null) {
            thumbnail = new JsonAttachment(quotedAttachment.getThumbnail());
        } else {
            thumbnail = null;
        }
    }
}
