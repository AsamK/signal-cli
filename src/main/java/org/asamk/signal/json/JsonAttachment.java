package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;

class JsonAttachment {

    String contentType;
    String filename;
    String id;
    int size;

    JsonAttachment(SignalServiceAttachment attachment) {
        this.contentType = attachment.getContentType();

        final SignalServiceAttachmentPointer pointer = attachment.asPointer();
        if (attachment.isPointer()) {
            this.id = String.valueOf(pointer.getId());
            if (pointer.getFileName().isPresent()) {
                this.filename = pointer.getFileName().get();
            }
            if (pointer.getSize().isPresent()) {
                this.size = pointer.getSize().get();
            }
        }
    }

    JsonAttachment(String filename) {
        this.filename = filename;
    }
}
