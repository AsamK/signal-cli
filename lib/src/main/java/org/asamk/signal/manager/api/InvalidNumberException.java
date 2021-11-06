package org.asamk.signal.manager.api;

public class InvalidNumberException extends Exception {

    InvalidNumberException(String message, Throwable e) {
        super(message, e);
    }
}
