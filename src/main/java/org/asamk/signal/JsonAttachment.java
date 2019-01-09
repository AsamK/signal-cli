package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;

class JsonAttachment {

    String contentType;
    String filename;
    long id;
    int size;

    JsonAttachment(SignalServiceAttachment attachment) {
        this.contentType = attachment.getContentType();

        final SignalServiceAttachmentPointer pointer = attachment.asPointer();
        if (attachment.isPointer()) {
            this.id = pointer.getId();
            if (pointer.getFileName().isPresent()) {
                this.filename = pointer.getFileName().get();
            }
            if (pointer.getSize().isPresent()) {
                this.size = pointer.getSize().get();
            }
        }
    }
}
