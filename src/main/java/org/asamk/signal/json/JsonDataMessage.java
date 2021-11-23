package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.Signal;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;
import java.util.stream.Collectors;

record JsonDataMessage(
        long timestamp,
        String message,
        Integer expiresInSeconds,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean viewOnce,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonReaction reaction,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonQuote quote,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonPayment payment,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonMention> mentions,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonAttachment> attachments,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonSticker sticker,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonRemoteDelete remoteDelete,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonSharedContact> contacts,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonGroupInfo groupInfo
) {

    static JsonDataMessage from(MessageEnvelope.Data dataMessage) {
        final var timestamp = dataMessage.timestamp();
        final var groupInfo = dataMessage.groupContext().isPresent() ? JsonGroupInfo.from(dataMessage.groupContext()
                .get()) : null;
        final var message = dataMessage.body().orElse(null);
        final var expiresInSeconds = dataMessage.expiresInSeconds();
        final var viewOnce = dataMessage.isViewOnce();
        final var reaction = dataMessage.reaction().map(JsonReaction::from).orElse(null);
        final var quote = dataMessage.quote().isPresent() ? JsonQuote.from(dataMessage.quote().get()) : null;
        final var payment = dataMessage.payment().isPresent() ? JsonPayment.from(dataMessage.payment().get()) : null;
        final var mentions = dataMessage.mentions().size() > 0 ? dataMessage.mentions()
                .stream()
                .map(JsonMention::from)
                .collect(Collectors.toList()) : null;
        final var remoteDelete = dataMessage.remoteDeleteId().isPresent()
                ? new JsonRemoteDelete(dataMessage.remoteDeleteId().get())
                : null;
        final var attachments = dataMessage.attachments().size() > 0 ? dataMessage.attachments()
                .stream()
                .map(JsonAttachment::from)
                .collect(Collectors.toList()) : null;
        final var sticker = dataMessage.sticker().isPresent() ? JsonSticker.from(dataMessage.sticker().get()) : null;

        final var contacts = dataMessage.sharedContacts().size() > 0 ? dataMessage.sharedContacts()
                .stream()
                .map(JsonSharedContact::from)
                .collect(Collectors.toList()) : null;
        return new JsonDataMessage(timestamp,
                message,
                expiresInSeconds,
                viewOnce,
                reaction,
                quote,
                payment,
                mentions,
                attachments,
                sticker,
                remoteDelete,
                contacts,
                groupInfo);
    }

    static JsonDataMessage from(Signal.MessageReceived messageReceived) {
        return new JsonDataMessage(messageReceived.getTimestamp(),
                messageReceived.getMessage(),
                // TODO Replace these with the proper commands
                null,
                null,
                null,
                null,
                null,
                null,
                messageReceived.getAttachments().stream().map(JsonAttachment::from).collect(Collectors.toList()),
                null,
                null,
                null,
                messageReceived.getGroupId().length > 0 ? JsonGroupInfo.from(messageReceived.getGroupId()) : null);
    }

    static JsonDataMessage from(Signal.SyncMessageReceived messageReceived) {
        return new JsonDataMessage(messageReceived.getTimestamp(),
                messageReceived.getMessage(),
                // TODO Replace these with the proper commands
                null,
                null,
                null,
                null,
                null,
                null,
                messageReceived.getAttachments().stream().map(JsonAttachment::from).collect(Collectors.toList()),
                null,
                null,
                null,
                messageReceived.getGroupId().length > 0 ? JsonGroupInfo.from(messageReceived.getGroupId()) : null);
    }
}
