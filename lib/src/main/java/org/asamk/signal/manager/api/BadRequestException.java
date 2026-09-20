package org.asamk.signal.manager.api;

import org.signal.libsignal.net.BadRequestError;

import java.io.IOException;

public class BadRequestException extends IOException {

    private final BadRequestError error;

    public BadRequestException(final BadRequestError error) {
        super(error.toString());
        this.error = error;
    }

    public BadRequestError getError() {
        return error;
    }
}
