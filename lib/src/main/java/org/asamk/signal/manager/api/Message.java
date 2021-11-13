package org.asamk.signal.manager.api;

import java.util.List;

public record Message(String messageText, List<String> attachments, List<Mention> mentions) {

    public record Mention(RecipientIdentifier.Single recipient, int start, int length) {}
}
