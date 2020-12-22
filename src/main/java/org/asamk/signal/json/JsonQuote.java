package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.ArrayList;
import java.util.List;

public class JsonQuote {

    long id;
    String author;
    String text;
    List<JsonAttachment> attachments;

    JsonQuote(SignalServiceDataMessage.Quote quote) {
        this.id = quote.getId();
        this.author = quote.getAuthor().getLegacyIdentifier();
        this.text = quote.getText();

        if (quote.getAttachments().size() > 0) {
            this.attachments = new ArrayList<>(quote.getAttachments().size());

            SignalServiceAttachmentPointer attachmentPointer;
            for (SignalServiceDataMessage.Quote.QuotedAttachment quotedAttachment : quote.getAttachments()) {
                JsonAttachment recentAttachment = new JsonAttachment(quotedAttachment.getThumbnail());

                // Its possible the name might be missing, if it is then we'll use the other one
                attachmentPointer = quotedAttachment.getThumbnail().asPointer();
                if (!attachmentPointer.getFileName().isPresent()) {
                    recentAttachment.filename = quotedAttachment.getFileName();
                }

                this.attachments.add(recentAttachment);
            }
        } else {
            this.attachments = new ArrayList<>();
        }
    }

}
