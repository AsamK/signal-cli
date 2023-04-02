package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

record JsonAttachment(
        String contentType,
        String filename,
        String id,
        Long size,
        Integer width,
        Integer height,
        String caption,
        Long uploadTimestamp
) {

    static JsonAttachment from(MessageEnvelope.Data.Attachment attachment) {
        final var id = attachment.id().orElse(null);
        final var filename = attachment.fileName().orElse(null);
        final var size = attachment.size().orElse(null);
        final var width = attachment.width().orElse(null);
        final var height = attachment.height().orElse(null);
        final var caption = attachment.caption().orElse(null);
        final var uploadTimestamp = attachment.uploadTimestamp().orElse(null);

        return new JsonAttachment(attachment.contentType(),
                filename,
                id,
                size,
                width,
                height,
                caption,
                uploadTimestamp);
    }
}
