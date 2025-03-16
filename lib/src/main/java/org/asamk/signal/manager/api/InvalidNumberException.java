package org.asamk.signal.manager.api;

public class InvalidNumberException extends Exception {

    public InvalidNumberException(String message) {
        super(message);
    }

    InvalidNumberException(String message, Throwable e) {
        super(message, e);
    }
}
