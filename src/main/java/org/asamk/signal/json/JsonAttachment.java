package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

record JsonAttachment(String contentType, String filename, String id, Long size) {

    static JsonAttachment from(SignalServiceAttachment attachment) {
        if (attachment.isPointer()) {
            final var pointer = attachment.asPointer();
            final var id = pointer.getRemoteId().toString();
            final var filename = pointer.getFileName().orNull();
            final var size = pointer.getSize().transform(Integer::longValue).orNull();
            return new JsonAttachment(attachment.getContentType(), filename, id, size);
        } else {
            final var stream = attachment.asStream();
            final var filename = stream.getFileName().orNull();
            final var size = stream.getLength();
            return new JsonAttachment(attachment.getContentType(), filename, null, size);
        }
    }

    static JsonAttachment from(String filename) {
        return new JsonAttachment(filename, null, null, null);
    }
}
