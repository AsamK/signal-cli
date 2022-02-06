package org.asamk.signal.manager.api;

public class AccountCheckException extends Exception {

    public AccountCheckException(String message) {
        super(message);
    }

    public AccountCheckException(String message, Exception e) {
        super(message, e);
    }
}
