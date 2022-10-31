package org.asamk.signal.http;

public class HttpServerException extends RuntimeException {

    private int httpStatus;

    public HttpServerException(final int aHttpStatus, final String message) {
        super(message);
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
