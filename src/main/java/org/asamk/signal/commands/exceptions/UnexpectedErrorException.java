package org.asamk.signal.commands.exceptions;

public final class UnexpectedErrorException extends CommandException {

    public UnexpectedErrorException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
