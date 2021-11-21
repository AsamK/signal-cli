package org.asamk.signal.manager.api;

import java.util.List;
import java.util.Optional;

public record Message(String messageText, List<String> attachments, List<Mention> mentions, Optional<Quote> quote) {

    public record Mention(RecipientIdentifier.Single recipient, int start, int length) {}

    public record Quote(long timestamp, RecipientIdentifier.Single author, String message, List<Mention> mentions) {}
}
