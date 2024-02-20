package org.asamk.signal.manager.api;

public class VerificationMethoNotAvailableException extends Exception {

    public VerificationMethoNotAvailableException() {
        super("Invalid verification method");
    }
}
