package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.List;
import java.util.stream.Collectors;

record JsonDataMessage(
        long timestamp,
        String message,
        Integer expiresInSeconds,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean viewOnce,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonReaction reaction,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonQuote quote,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonMention> mentions,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonAttachment> attachments,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonSticker sticker,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonRemoteDelete remoteDelete,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonSharedContact> contacts,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonGroupInfo groupInfo
) {

    static JsonDataMessage from(SignalServiceDataMessage dataMessage, Manager m) {
        final var timestamp = dataMessage.getTimestamp();
        final JsonGroupInfo groupInfo;
        if (dataMessage.getGroupContext().isPresent()) {
            final var groupContext = dataMessage.getGroupContext().get();
            if (groupContext.getGroupV1().isPresent()) {
                var group = groupContext.getGroupV1().get();
                groupInfo = JsonGroupInfo.from(group);
            } else if (groupContext.getGroupV2().isPresent()) {
                var group = groupContext.getGroupV2().get();
                groupInfo = JsonGroupInfo.from(group);
            } else {
                groupInfo = null;
            }
        } else {
            groupInfo = null;
        }
        final var message = dataMessage.getBody().orNull();
        final var expiresInSeconds = dataMessage.getExpiresInSeconds();
        final var viewOnce = dataMessage.isViewOnce();
        final var reaction = dataMessage.getReaction().isPresent() ? JsonReaction.from(dataMessage.getReaction().get(),
                m) : null;
        final var quote = dataMessage.getQuote().isPresent() ? JsonQuote.from(dataMessage.getQuote().get(), m) : null;
        final List<JsonMention> mentions;
        if (dataMessage.getMentions().isPresent()) {
            mentions = dataMessage.getMentions()
                    .get()
                    .stream()
                    .map(mention -> JsonMention.from(mention, m))
                    .collect(Collectors.toList());
        } else {
            mentions = List.of();
        }
        final var remoteDelete = dataMessage.getRemoteDelete().isPresent()
                ? JsonRemoteDelete.from(dataMessage.getRemoteDelete().get())
                : null;
        final List<JsonAttachment> attachments;
        if (dataMessage.getAttachments().isPresent()) {
            attachments = dataMessage.getAttachments()
                    .get()
                    .stream()
                    .map(JsonAttachment::from)
                    .collect(Collectors.toList());
        } else {
            attachments = List.of();
        }
        final var sticker = dataMessage.getSticker().isPresent()
                ? JsonSticker.from(dataMessage.getSticker().get())
                : null;

        final List<JsonSharedContact> contacts;
        if (dataMessage.getSharedContacts().isPresent()) {
            contacts = dataMessage.getSharedContacts()
                    .get()
                    .stream()
                    .map(JsonSharedContact::from)
                    .collect(Collectors.toList());
        } else {
            contacts = List.of();
        }
        return new JsonDataMessage(timestamp,
                message,
                expiresInSeconds,
                viewOnce,
                reaction,
                quote,
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
                messageReceived.getAttachments().stream().map(JsonAttachment::from).collect(Collectors.toList()),
                null,
                null,
                null,
                messageReceived.getGroupId().length > 0 ? JsonGroupInfo.from(messageReceived.getGroupId()) : null);
    }
}
