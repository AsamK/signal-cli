package org.asamk.signal.manager.api;

import java.util.List;
import java.util.Optional;

public record Message(
        String messageText,
        List<String> attachments,
        List<AttachmentDimensions> attachmentDimensions,
        boolean viewOnce,
        boolean voiceNote,
        List<Mention> mentions,
        Optional<Quote> quote,
        Optional<Sticker> sticker,
        List<Preview> previews,
        Optional<StoryReply> storyReply,
        List<TextStyle> textStyles,
        boolean urgent
) {

    public record AttachmentDimensions(int width, int height) {

        public static final AttachmentDimensions UNKNOWN = new AttachmentDimensions(0, 0);

        public AttachmentDimensions {
            if (width < 0 || height < 0 || (width == 0) != (height == 0)) {
                throw new IllegalArgumentException("Dimensions must both be positive or both zero");
            }
        }
    }

    public record Mention(RecipientIdentifier.Single recipient, int start, int length) {}

    public record Quote(
            long timestamp,
            RecipientIdentifier.Single author,
            String message,
            List<Mention> mentions,
            List<TextStyle> textStyles,
            List<Attachment> attachments
    ) {

        public record Attachment(String contentType, String filename, String preview) {}
    }

    public record Sticker(byte[] packId, int stickerId) {}

    public record Preview(String url, String title, String description, Optional<String> image) {}

    public record StoryReply(long timestamp, RecipientIdentifier.Single author) {}
}
