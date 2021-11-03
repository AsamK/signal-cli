package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record JsonQuote(
        long id,
        @Deprecated String author,
        String authorNumber,
        String authorUuid,
        String text,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonMention> mentions,
        List<JsonQuotedAttachment> attachments
) {

    static JsonQuote from(MessageEnvelope.Data.Quote quote) {
        final var id = quote.id();
        final var address = quote.author();
        final var author = address.getLegacyIdentifier();
        final var authorNumber = address.getNumber().orElse(null);
        final var authorUuid = address.getUuid().map(UUID::toString).orElse(null);
        final var text = quote.text().orElse(null);

        final var mentions = quote.mentions().size() > 0 ? quote.mentions()
                .stream()
                .map(JsonMention::from)
                .collect(Collectors.toList()) : null;

        final var attachments = quote.attachments().size() > 0 ? quote.attachments()
                .stream()
                .map(JsonQuotedAttachment::from)
                .collect(Collectors.toList()) : new ArrayList<JsonQuotedAttachment>();

        return new JsonQuote(id, author, authorNumber, authorUuid, text, mentions, attachments);
    }
}
