package org.asamk.signal.manager.api;

import java.util.List;
import java.util.Optional;

public record Message(
        String messageText,
        List<String> attachments,
        List<Mention> mentions,
        Optional<Quote> quote,
        Optional<Sticker> sticker,
        List<Preview> previews,
        Optional<StoryReply> storyReply
) {

    public record Mention(RecipientIdentifier.Single recipient, int start, int length) {}

    public record Quote(long timestamp, RecipientIdentifier.Single author, String message, List<Mention> mentions) {}

    public record Sticker(byte[] packId, int stickerId) {}

    public record Preview(String url, String title, String description, Optional<String> image) {}

    public record StoryReply(long timestamp, RecipientIdentifier.Single author) {}
}
