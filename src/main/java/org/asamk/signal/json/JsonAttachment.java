package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

class JsonAttachment {

    @JsonProperty
    final String contentType;

    @JsonProperty
    final String filename;

    @JsonProperty
    final String id;

    @JsonProperty
    final Long size;

    JsonAttachment(SignalServiceAttachment attachment) {
        this.contentType = attachment.getContentType();

        if (attachment.isPointer()) {
            final var pointer = attachment.asPointer();
            this.id = pointer.getRemoteId().toString();
            this.filename = pointer.getFileName().orNull();
            this.size = pointer.getSize().transform(Integer::longValue).orNull();
        } else {
            final var stream = attachment.asStream();
            this.id = null;
            this.filename = stream.getFileName().orNull();
            this.size = stream.getLength();
        }
    }

    JsonAttachment(String filename) {
        this.filename = filename;
        this.contentType = null;
        this.id = null;
        this.size = null;
    }
}
