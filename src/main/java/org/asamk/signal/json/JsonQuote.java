package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class JsonQuote {

    @JsonProperty
    final long id;

    @JsonProperty
    final String author;

    @JsonProperty
    final String text;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<JsonMention> mentions;

    @JsonProperty
    final List<JsonQuotedAttachment> attachments;

    JsonQuote(SignalServiceDataMessage.Quote quote, Manager m) {
        this.id = quote.getId();
        this.author = getLegacyIdentifier(m.resolveSignalServiceAddress(quote.getAuthor()));
        this.text = quote.getText();

        if (quote.getMentions() != null && quote.getMentions().size() > 0) {
            this.mentions = quote.getMentions()
                    .stream()
                    .map(quotedMention -> new JsonMention(quotedMention, m))
                    .collect(Collectors.toList());
        } else {
            this.mentions = null;
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
