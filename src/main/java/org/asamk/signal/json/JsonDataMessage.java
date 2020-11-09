package org.asamk.signal.json;

import org.asamk.Signal;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
//import org.whispersystems.libsignal.util.guava.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


// i think this is what you have to do to get another dict in json
// but i'm not sure
class JsonReaction {
    String emoji; // unicode??
    String targetAuthor;
    long targetTimestamp;
    boolean isRemove;
	JsonReaction (SignalServiceDataMessage.Reaction reaction) {
        this.emoji = reaction.getEmoji();
        // comment on this line from ReceiveMessageHandler: todo resolve
        this.targetAuthor = reaction.getTargetAuthor().getLegacyIdentifier();
    	this.targetTimestamp = reaction.getTargetSentTimestamp();
    	this.isRemove = reaction.isRemove();
    }
}


class JsonDataMessage {

    long timestamp;
    String message;
    int expiresInSeconds;
    List<JsonAttachment> attachments;
    JsonGroupInfo groupInfo;
    JsonReaction reaction;
	SignalServiceDataMessage.Quote quote;

    JsonDataMessage(SignalServiceDataMessage dataMessage) {
        this.timestamp = dataMessage.getTimestamp();
        if (dataMessage.getGroupContext().isPresent() && dataMessage.getGroupContext().get().getGroupV1().isPresent()) {
            SignalServiceGroup groupInfo = dataMessage.getGroupContext().get().getGroupV1().get();
            this.groupInfo = new JsonGroupInfo(groupInfo);
        }
        if (dataMessage.getBody().isPresent()) {
            this.message = dataMessage.getBody().get();
        }
        this.expiresInSeconds = dataMessage.getExpiresInSeconds();
        if (dataMessage.getAttachments().isPresent()) {
            this.attachments = new ArrayList<>(dataMessage.getAttachments().get().size());
            for (SignalServiceAttachment attachment : dataMessage.getAttachments().get()) {
                this.attachments.add(new JsonAttachment(attachment));
            }
        } else {
            this.attachments = new ArrayList<>();
        }
        if (dataMessage.getReaction().isPresent()) {
            final SignalServiceDataMessage.Reaction reaction = dataMessage.getReaction().get();
            this.reaction = new JsonReaction(reaction);
/*            this.emoji = reaction.getEmoji();
            // comment on this line from ReceiveMessageHandler: todo resolve
            this.targetAuthor = reaction.getTargetAuthor().getLegacyIdentifier();
			this.targetTimestamp = reaction.getTargetSentTimestamp();
*/        } /*else {
			this.reaction = null;
/*
            this.emoji = "";
            this.targetAuthor = "";
            this.targetTimestamp = 0;

*/ //       }
/*

        if (message.getQuote().isPresent()) {
            SignalServiceDataMessage.Quote quote = message.getQuote().get();
            System.out.println("Quote: (" + quote.getId() + ")");
            // there doesn't seem to be any fucking way to find a message's id?
            System.out.println(" Author: " + quote.getAuthor().getLegacyIdentifier());
            System.out.println(" Text: " + quote.getText());
        }
        if (message.isExpirationUpdate()) {
            System.out.println("Is Expiration update: " + message.isExpirationUpdate());
        }
*/
    }
	// very confusingly MessageReceived seems to be only made in JsonDbusReceiveMessageHandler
	// and only when *sending* to dbus, so to my current understanding this never gets called
	// which would suggest i'm not understanding something
    public JsonDataMessage(Signal.MessageReceived messageReceived) {
        timestamp = messageReceived.getTimestamp();
        message = messageReceived.getMessage();
        groupInfo = new JsonGroupInfo(messageReceived.getGroupId());
        attachments = messageReceived.getAttachments()
                .stream()
                .map(JsonAttachment::new)
                .collect(Collectors.toList());
    }
	// i don't understand what SyncMessages are so i'm gonna ignore them
	// i think it only matters if you have multiple devices on your end
    public JsonDataMessage(Signal.SyncMessageReceived messageReceived) {
        timestamp = messageReceived.getTimestamp();
        message = messageReceived.getMessage();
        groupInfo = new JsonGroupInfo(messageReceived.getGroupId());
        attachments = messageReceived.getAttachments()
                .stream()
                .map(JsonAttachment::new)
                .collect(Collectors.toList());
    }
}
