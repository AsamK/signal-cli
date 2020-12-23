package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public class JsonQuotedAttachment {

    String contentType;
    String filename;
    JsonAttachment thumbnail;

    JsonQuotedAttachment(SignalServiceDataMessage.Quote.QuotedAttachment quotedAttachment) {
        contentType = quotedAttachment.getContentType();
        filename = quotedAttachment.getFileName();
        if (quotedAttachment.getThumbnail() != null) {
            thumbnail = new JsonAttachment(quotedAttachment.getThumbnail());
        }
        else {
            thumbnail = null;
        }
    }
}
