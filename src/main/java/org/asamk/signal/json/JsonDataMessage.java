package org.asamk.signal.json;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class JsonDataMessage {

    long timestamp;
    String message;
    int expiresInSeconds;

    JsonReaction reaction;
    JsonQuote quote;
    List<JsonMention> mentions;
    List<JsonAttachment> attachments;
    JsonGroupInfo groupInfo;

    JsonDataMessage(SignalServiceDataMessage dataMessage, Manager m) {
        this.timestamp = dataMessage.getTimestamp();
        if (dataMessage.getGroupContext().isPresent()) {
            if (dataMessage.getGroupContext().get().getGroupV1().isPresent()) {
                SignalServiceGroup groupInfo = dataMessage.getGroupContext().get().getGroupV1().get();
                this.groupInfo = new JsonGroupInfo(groupInfo);
            } else if (dataMessage.getGroupContext().get().getGroupV2().isPresent()) {
                SignalServiceGroupV2 groupInfo = dataMessage.getGroupContext().get().getGroupV2().get();
                this.groupInfo = new JsonGroupInfo(groupInfo);
            }
        }
        if (dataMessage.getBody().isPresent()) {
            this.message = dataMessage.getBody().get();
        }
        this.expiresInSeconds = dataMessage.getExpiresInSeconds();
        if (dataMessage.getReaction().isPresent()) {
            this.reaction = new JsonReaction(dataMessage.getReaction().get(), m);
        }
        if (dataMessage.getQuote().isPresent()) {
            this.quote = new JsonQuote(dataMessage.getQuote().get(), m);
        }
        if (dataMessage.getMentions().isPresent()) {
            this.mentions = new ArrayList<>(dataMessage.getMentions().get().size());
            for (SignalServiceDataMessage.Mention mention : dataMessage.getMentions().get()) {
                this.mentions.add(new JsonMention(mention, m));
            }
        } else {
            this.mentions = new ArrayList<>();
        }
        if (dataMessage.getAttachments().isPresent()) {
            this.attachments = new ArrayList<>(dataMessage.getAttachments().get().size());
            for (SignalServiceAttachment attachment : dataMessage.getAttachments().get()) {
                this.attachments.add(new JsonAttachment(attachment));
            }
        } else {
            this.attachments = new ArrayList<>();
        }
    }

    public JsonDataMessage(Signal.MessageReceived messageReceived) {
        timestamp = messageReceived.getTimestamp();
        message = messageReceived.getMessage();
        groupInfo = new JsonGroupInfo(messageReceived.getGroupId());
        reaction = null;    // TODO Replace these 3 with the proper commands
        quote = null;
        mentions = null;
        attachments = messageReceived.getAttachments().stream().map(JsonAttachment::new).collect(Collectors.toList());
    }

    public JsonDataMessage(Signal.SyncMessageReceived messageReceived) {
        timestamp = messageReceived.getTimestamp();
        message = messageReceived.getMessage();
        groupInfo = new JsonGroupInfo(messageReceived.getGroupId());
        reaction = null;    // TODO Replace these 3 with the proper commands
        quote = null;
        mentions = null;
        attachments = messageReceived.getAttachments().stream().map(JsonAttachment::new).collect(Collectors.toList());
    }
}
