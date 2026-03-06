package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;
import java.util.UUID;

@JsonSchema(title = "Quote")
public record JsonQuote(
        @JsonProperty(required = true) long id,
        @Deprecated String author,
        @JsonProperty(required = true) String authorNumber,
        @JsonProperty(required = true) String authorUuid,
        @JsonProperty(required = true) String text,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonMention> mentions,
        @JsonProperty(required = true) List<JsonQuotedAttachment> attachments,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonTextStyle> textStyles
) {

    static JsonQuote from(MessageEnvelope.Data.Quote quote) {
        final var id = quote.id();
        final var address = quote.author();
        final var author = address.getLegacyIdentifier();
        final var authorNumber = address.number().orElse(null);
        final var authorUuid = address.uuid().map(UUID::toString).orElse(null);
        final var text = quote.text().orElse(null);

        final var mentions = !quote.mentions().isEmpty()
                ? quote.mentions().stream().map(JsonMention::from).toList()
                : null;

        final var attachments = !quote.attachments().isEmpty() ? quote.attachments()
                .stream()
                .map(JsonQuotedAttachment::from)
                .toList() : List.<JsonQuotedAttachment>of();

        final var textStyles = !quote.textStyles().isEmpty() ? quote.textStyles()
                .stream()
                .map(JsonTextStyle::from)
                .toList() : null;

        return new JsonQuote(id, author, authorNumber, authorUuid, text, mentions, attachments, textStyles);
    }
}
