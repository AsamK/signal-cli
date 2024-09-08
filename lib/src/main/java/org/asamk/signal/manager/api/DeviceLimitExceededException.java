package org.asamk.signal.manager.api;

public class DeviceLimitExceededException extends Exception {

    public DeviceLimitExceededException(final String message) {
        super(message);
    }

    public DeviceLimitExceededException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
