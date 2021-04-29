package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.List;
import java.util.stream.Collectors;

class JsonDataMessage {

    @JsonProperty
    final long timestamp;

    @JsonProperty
    final String message;

    @JsonProperty
    final Integer expiresInSeconds;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final Boolean viewOnce;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonReaction reaction;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonQuote quote;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<JsonMention> mentions;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<JsonAttachment> attachments;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonSticker sticker;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonRemoteDelete remoteDelete;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<JsonSharedContact> contacts;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonGroupInfo groupInfo;

    JsonDataMessage(SignalServiceDataMessage dataMessage, Manager m) {
        this.timestamp = dataMessage.getTimestamp();
        if (dataMessage.getGroupContext().isPresent()) {
            final var groupContext = dataMessage.getGroupContext().get();
            if (groupContext.getGroupV1().isPresent()) {
                var groupInfo = groupContext.getGroupV1().get();
                this.groupInfo = new JsonGroupInfo(groupInfo);
            } else if (groupContext.getGroupV2().isPresent()) {
                var groupInfo = groupContext.getGroupV2().get();
                this.groupInfo = new JsonGroupInfo(groupInfo);
            } else {
                this.groupInfo = null;
            }
        } else {
            this.groupInfo = null;
        }
        this.message = dataMessage.getBody().orNull();
        this.expiresInSeconds = dataMessage.getExpiresInSeconds();
        this.viewOnce = dataMessage.isViewOnce();
        this.reaction = dataMessage.getReaction().isPresent()
                ? new JsonReaction(dataMessage.getReaction().get(), m)
                : null;
        this.quote = dataMessage.getQuote().isPresent() ? new JsonQuote(dataMessage.getQuote().get(), m) : null;
        if (dataMessage.getMentions().isPresent()) {
            this.mentions = dataMessage.getMentions()
                    .get()
                    .stream()
                    .map(mention -> new JsonMention(mention, m))
                    .collect(Collectors.toList());
        } else {
            this.mentions = List.of();
        }
        remoteDelete = dataMessage.getRemoteDelete().isPresent() ? new JsonRemoteDelete(dataMessage.getRemoteDelete()
                .get()) : null;
        if (dataMessage.getAttachments().isPresent()) {
            this.attachments = dataMessage.getAttachments()
                    .get()
                    .stream()
                    .map(JsonAttachment::new)
                    .collect(Collectors.toList());
        } else {
            this.attachments = List.of();
        }
        this.sticker = dataMessage.getSticker().isPresent() ? new JsonSticker(dataMessage.getSticker().get()) : null;

        if (dataMessage.getSharedContacts().isPresent()) {
            this.contacts = dataMessage.getSharedContacts()
                    .get()
                    .stream()
                    .map(JsonSharedContact::new)
                    .collect(Collectors.toList());
        } else {
            this.contacts = List.of();
        }
    }

    public JsonDataMessage(Signal.MessageReceived messageReceived) {
        timestamp = messageReceived.getTimestamp();
        message = messageReceived.getMessage();
        groupInfo = messageReceived.getGroupId().length > 0 ? new JsonGroupInfo(messageReceived.getGroupId()) : null;
        expiresInSeconds = null;
        viewOnce = null;
        remoteDelete = null;
        reaction = null;    // TODO Replace these 5 with the proper commands
        quote = null;
        mentions = null;
        sticker = null;
        contacts = null;
        attachments = messageReceived.getAttachments().stream().map(JsonAttachment::new).collect(Collectors.toList());
    }

    public JsonDataMessage(Signal.SyncMessageReceived messageReceived) {
        timestamp = messageReceived.getTimestamp();
        message = messageReceived.getMessage();
        groupInfo = messageReceived.getGroupId().length > 0 ? new JsonGroupInfo(messageReceived.getGroupId()) : null;
        expiresInSeconds = null;
        viewOnce = null;
        remoteDelete = null;
        reaction = null;    // TODO Replace these 5 with the proper commands
        quote = null;
        mentions = null;
        sticker = null;
        contacts = null;
        attachments = messageReceived.getAttachments().stream().map(JsonAttachment::new).collect(Collectors.toList());
    }
}
