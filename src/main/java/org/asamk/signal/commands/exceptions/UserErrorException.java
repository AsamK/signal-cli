package org.asamk.signal.commands.exceptions;

public final class UserErrorException extends CommandException {

    public UserErrorException(final String message) {
        super(message);
    }

    public UserErrorException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
