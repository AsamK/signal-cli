package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public record JsonQuote(
        long id,
        @Deprecated String author,
        String authorNumber,
        String authorUuid,
        String text,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonMention> mentions,
        List<JsonQuotedAttachment> attachments
) {

    static JsonQuote from(SignalServiceDataMessage.Quote quote, Manager m) {
        final var id = quote.getId();
        final var address = m.resolveSignalServiceAddress(quote.getAuthor());
        final var author = getLegacyIdentifier(address);
        final var authorNumber = address.getNumber().orNull();
        final var authorUuid = address.getUuid().toString();
        final var text = quote.getText();

        final List<JsonMention> mentions;
        if (quote.getMentions() != null && quote.getMentions().size() > 0) {
            mentions = quote.getMentions()
                    .stream()
                    .map(quotedMention -> JsonMention.from(quotedMention, m))
                    .collect(Collectors.toList());
        } else {
            mentions = null;
        }

        final List<JsonQuotedAttachment> attachments;
        if (quote.getAttachments().size() > 0) {
            attachments = quote.getAttachments().stream().map(JsonQuotedAttachment::from).collect(Collectors.toList());
        } else {
            attachments = new ArrayList<>();
        }

        return new JsonQuote(id, author, authorNumber, authorUuid, text, mentions, attachments);
    }
}
