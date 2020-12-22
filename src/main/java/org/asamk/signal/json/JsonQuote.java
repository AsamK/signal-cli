package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.ArrayList;
import java.util.List;

public class JsonQuote {

    long id;
    String author;
    String text;
    List<JsonAttachment> attachments;

    JsonQuote(SignalServiceDataMessage.Quote quote){
        this.id = quote.getId();
        this.author = quote.getAuthor().getLegacyIdentifier();
        this.text = quote.getText();

        if (quote.getAttachments().size() > 0) {
            this.attachments = new ArrayList<>(quote.getAttachments().size());
            for (SignalServiceDataMessage.Quote.QuotedAttachment quotedAttachment : quote.getAttachments()) {
                // We use this constructor to override the filename since the one in the thumbnail is lost
                this.attachments.add(new JsonAttachment(
                        quotedAttachment.getThumbnail(),
                        quotedAttachment.getFileName()
                ));
            }
        } else {
            this.attachments = new ArrayList<>();
        }
    }

}
