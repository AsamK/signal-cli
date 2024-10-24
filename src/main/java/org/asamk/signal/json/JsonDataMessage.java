package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;

record JsonDataMessage(
        long timestamp,
        String message,
        Integer expiresInSeconds,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean viewOnce,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonReaction reaction,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonQuote quote,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonPayment payment,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonMention> mentions,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonPreview> previews,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonAttachment> attachments,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonSticker sticker,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonRemoteDelete remoteDelete,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonSharedContact> contacts,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonTextStyle> textStyles,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonGroupInfo groupInfo,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonStoryContext storyContext
) {

    static JsonDataMessage from(MessageEnvelope.Data dataMessage, Manager m) {
        final var timestamp = dataMessage.timestamp();
        final var groupInfo = dataMessage.groupContext().isPresent() ? JsonGroupInfo.from(dataMessage.groupContext()
                .get(), m) : null;
        final var storyContext = dataMessage.storyContext().isPresent()
                ? JsonStoryContext.from(dataMessage.storyContext().get())
                : null;
        final var message = dataMessage.body().orElse(null);
        final var expiresInSeconds = dataMessage.expiresInSeconds();
        final var viewOnce = dataMessage.isViewOnce();
        final var reaction = dataMessage.reaction().map(JsonReaction::from).orElse(null);
        final var quote = dataMessage.quote().isPresent() ? JsonQuote.from(dataMessage.quote().get()) : null;
        final var payment = dataMessage.payment().isPresent() ? JsonPayment.from(dataMessage.payment().get()) : null;
        final var mentions = !dataMessage.mentions().isEmpty() ? dataMessage.mentions()
                .stream()
                .map(JsonMention::from)
                .toList() : null;
        final var previews = !dataMessage.previews().isEmpty() ? dataMessage.previews()
                .stream()
                .map(JsonPreview::from)
                .toList() : null;
        final var remoteDelete = dataMessage.remoteDeleteId().isPresent()
                ? new JsonRemoteDelete(dataMessage.remoteDeleteId().get())
                : null;
        final var attachments = !dataMessage.attachments().isEmpty() ? dataMessage.attachments()
                .stream()
                .map(JsonAttachment::from)
                .toList() : null;
        final var sticker = dataMessage.sticker().isPresent() ? JsonSticker.from(dataMessage.sticker().get()) : null;
        final var contacts = !dataMessage.sharedContacts().isEmpty() ? dataMessage.sharedContacts()
                .stream()
                .map(JsonSharedContact::from)
                .toList() : null;
        final var textStyles = !dataMessage.textStyles().isEmpty() ? dataMessage.textStyles()
                .stream()
                .map(JsonTextStyle::from)
                .toList() : null;

        return new JsonDataMessage(timestamp,
                message,
                expiresInSeconds,
                viewOnce,
                reaction,
                quote,
                payment,
                mentions,
                previews,
                attachments,
                sticker,
                remoteDelete,
                contacts,
                textStyles,
                groupInfo,
                storyContext);
    }
}
