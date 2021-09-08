package org.asamk.signal.commands.exceptions;

import java.io.IOException;

public final class IOErrorException extends CommandException {

    public IOErrorException(final String message, IOException cause) {
        super(message, cause);
    }
}
