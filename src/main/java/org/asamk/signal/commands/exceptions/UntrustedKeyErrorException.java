package org.asamk.signal.commands.exceptions;

public final class UntrustedKeyErrorException extends CommandException {

    public UntrustedKeyErrorException(final String message) {
        super(message);
    }
}
