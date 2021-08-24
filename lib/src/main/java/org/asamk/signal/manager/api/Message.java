package org.asamk.signal.manager.api;

import java.util.List;

public class Message {

    private final String messageText;
    private final List<String> attachments;

    public Message(final String messageText, final List<String> attachments) {
        this.messageText = messageText;
        this.attachments = attachments;
    }

    public String getMessageText() {
        return messageText;
    }

    public List<String> getAttachments() {
        return attachments;
    }
}
