package org.asamk.textsecure;

public class AttachmentInvalidException extends Exception {
    private final String attachment;

    public AttachmentInvalidException(String attachment, Exception e) {
        super(e);
        this.attachment = attachment;
    }

    public String getAttachment() {
        return attachment;
    }
}
