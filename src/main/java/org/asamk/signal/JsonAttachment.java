package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;

class JsonAttachment {
    String contentType;
    long id;
    int size;

    JsonAttachment(SignalServiceAttachment attachment) {
        this.contentType = attachment.getContentType();
        final SignalServiceAttachmentPointer pointer = attachment.asPointer();
        if (attachment.isPointer()) {
            this.id = pointer.getId();
            if (pointer.getSize().isPresent()) {
                this.size = pointer.getSize().get();
            }
        }
    }
}
