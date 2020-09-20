package org.asamk.signal.manager;

public class AttachmentInvalidException extends Exception {

    public AttachmentInvalidException(String message) {
        super(message);
    }

    public AttachmentInvalidException(String attachment, Exception e) {
        super(attachment + ": " + e.getMessage());
    }
}
