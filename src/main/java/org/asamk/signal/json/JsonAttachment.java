package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

record JsonAttachment(String contentType, String filename, String id, Long size) {

    static JsonAttachment from(MessageEnvelope.Data.Attachment attachment) {
        final var id = attachment.id().orElse(null);
        final var filename = attachment.fileName().orElse(null);
        final var size = attachment.size().orElse(null);
        return new JsonAttachment(attachment.contentType(), filename, id, size);
    }
}
