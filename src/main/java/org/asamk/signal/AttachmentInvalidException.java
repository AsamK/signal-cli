package org.asamk.signal;

import org.freedesktop.dbus.exceptions.DBusExecutionException;

public class AttachmentInvalidException extends DBusExecutionException {

    public AttachmentInvalidException(String message) {
        super(message);
    }

    public AttachmentInvalidException(String attachment, Exception e) {
        super(attachment + ": " + e.getMessage());
    }
}
