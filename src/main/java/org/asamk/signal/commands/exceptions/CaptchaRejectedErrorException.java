package org.asamk.signal.commands.exceptions;

import org.asamk.signal.manager.api.CaptchaRejectedException;

public final class CaptchaRejectedErrorException extends CommandException {

    public CaptchaRejectedErrorException(final String message, final CaptchaRejectedException cause) {
        super(message, cause);
    }
}
