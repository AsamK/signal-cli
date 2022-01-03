package org.asamk.signal.manager.api;

public class InvalidStickerException extends Exception {

    public InvalidStickerException(final String message) {
        super(message);
    }

    public InvalidStickerException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
