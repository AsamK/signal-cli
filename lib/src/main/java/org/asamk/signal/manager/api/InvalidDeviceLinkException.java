package org.asamk.signal.manager.api;

public class InvalidDeviceLinkException extends Exception {

    public InvalidDeviceLinkException(final String message) {
        super(message);
    }

    public InvalidDeviceLinkException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
