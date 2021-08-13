package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import org.asamk.signal.dbus.DbusAttachment;

class JsonAttachment {

    @JsonProperty
    final String contentType;

    @JsonProperty
    final String filename;

    @JsonProperty
    final String id;

    @JsonProperty
    final Long size;

    @JsonProperty
    Integer keyLength;

    @JsonProperty
    Integer width;

    @JsonProperty
    Integer height;

    @JsonProperty
    boolean voiceNote;

    @JsonProperty
    String caption;

    @JsonProperty
    String relay;

    @JsonProperty
    byte[] preview;

    @JsonProperty
    String digest;

    @JsonProperty
    String blurHash;

    JsonAttachment(SignalServiceAttachment attachment) {
        this.contentType = attachment.getContentType();

        if (attachment.isPointer()) {
            final var pointer = attachment.asPointer();
            this.id = pointer.getRemoteId().toString();
            this.filename = pointer.getFileName().orNull();
            this.size = pointer.getSize().transform(Integer::longValue).orNull();
            this.keyLength = pointer.getKey().length;
            this.width = pointer.getWidth();
            this.height = pointer.getHeight();
            this.voiceNote = pointer.getVoiceNote();
            if (pointer.getCaption().isPresent()) {this.caption = pointer.getCaption().get();}
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

    JsonAttachment(DbusAttachment attachment) {
        this.contentType = attachment.getContentType();
        this.id = attachment.getId();
        this.filename = attachment.getFileName();
        this.size = attachment.getFileSize();
        this.keyLength = attachment.getKeyLength();
        this.width = attachment.getWidth();
        this.height = attachment.getHeight();
        this.voiceNote = attachment.getVoiceNote();
        this.caption = attachment.getCaption();
    }

}
