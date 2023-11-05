package org.asamk.signal.commands.exceptions;

public sealed abstract class CommandException extends Exception permits IOErrorException, RateLimitErrorException, UnexpectedErrorException, UntrustedKeyErrorException, UserErrorException {

    public CommandException(final String message) {
        super(message);
    }

    public CommandException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
