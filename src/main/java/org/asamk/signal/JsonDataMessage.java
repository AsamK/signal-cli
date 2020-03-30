package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;

import java.util.ArrayList;
import java.util.List;

class JsonDataMessage {

    long timestamp;
    String message;
    int expiresInSeconds;
    List<JsonAttachment> attachments;
    JsonGroupInfo groupInfo;

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
    }
}
