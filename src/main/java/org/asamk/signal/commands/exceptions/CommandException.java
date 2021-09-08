package org.asamk.signal.commands.exceptions;

public class CommandException extends Exception {

    public CommandException(final String message) {
        super(message);
    }

    public CommandException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
