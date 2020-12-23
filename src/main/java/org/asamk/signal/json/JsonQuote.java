package org.asamk.signal.json;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.ArrayList;
import java.util.List;

public class JsonQuote {

    long id;
    String author;
    String text;

    List<JsonMention> mentions;
    List<JsonQuotedAttachment> attachments;

    JsonQuote(SignalServiceDataMessage.Quote quote, Manager m) {
        this.id = quote.getId();
        this.author = m.resolveSignalServiceAddress(quote.getAuthor()).getLegacyIdentifier();
        this.text = quote.getText();

        if (quote.getMentions().size() > 0) {
            this.mentions = new ArrayList<>(quote.getMentions().size());

            for (SignalServiceDataMessage.Mention quotedMention: quote.getMentions()){
                this.mentions.add(new JsonMention(quotedMention, m));
            }
        }

        if (quote.getAttachments().size() > 0) {
            this.attachments = new ArrayList<>(quote.getAttachments().size());

            for (SignalServiceDataMessage.Quote.QuotedAttachment quotedAttachment : quote.getAttachments()) {
                this.attachments.add(new JsonQuotedAttachment(quotedAttachment));
            }
        } else {
            this.attachments = new ArrayList<>();
        }
    }

}
