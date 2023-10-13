package org.asamk.signal.commands.exceptions;

import org.asamk.signal.manager.api.RateLimitException;

public final class RateLimitErrorException extends CommandException {

    public RateLimitErrorException(final String message, final RateLimitException cause) {
        super(message, cause);
    }
}
