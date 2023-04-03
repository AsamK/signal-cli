package org.asamk.signal.manager.api;

public class InvalidUsernameException extends Exception {

    public InvalidUsernameException(final String message) {
        super(message);
    }

    public InvalidUsernameException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
