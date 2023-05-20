package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;
import java.util.UUID;

public record JsonQuote(
        long id,
        @Deprecated String author,
        String authorNumber,
        String authorUuid,
        String text,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonMention> mentions,
        List<JsonQuotedAttachment> attachments,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonTextStyle> textStyles
) {

    static JsonQuote from(MessageEnvelope.Data.Quote quote) {
        final var id = quote.id();
        final var address = quote.author();
        final var author = address.getLegacyIdentifier();
        final var authorNumber = address.number().orElse(null);
        final var authorUuid = address.uuid().map(UUID::toString).orElse(null);
        final var text = quote.text().orElse(null);

        final var mentions = quote.mentions().size() > 0
                ? quote.mentions().stream().map(JsonMention::from).toList()
                : null;

        final var attachments = quote.attachments().size() > 0 ? quote.attachments()
                .stream()
                .map(JsonQuotedAttachment::from)
                .toList() : List.<JsonQuotedAttachment>of();

        final var textStyles = quote.textStyles().size() > 0 ? quote.textStyles()
                .stream()
                .map(JsonTextStyle::from)
                .toList() : null;

        return new JsonQuote(id, author, authorNumber, authorUuid, text, mentions, attachments, textStyles);
    }
}
