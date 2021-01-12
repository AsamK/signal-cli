package org.asamk.signal.json;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

        if (quote.getMentions() != null && quote.getMentions().size() > 0) {
            this.mentions = quote.getMentions()
                    .stream()
                    .map(quotedMention -> new JsonMention(quotedMention, m))
                    .collect(Collectors.toList());
        }

        if (quote.getAttachments().size() > 0) {
            this.attachments = quote.getAttachments()
                    .stream()
                    .map(JsonQuotedAttachment::new)
                    .collect(Collectors.toList());
        } else {
            this.attachments = new ArrayList<>();
        }
    }
}
