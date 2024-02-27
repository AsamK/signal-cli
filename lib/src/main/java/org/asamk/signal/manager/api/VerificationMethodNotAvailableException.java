package org.asamk.signal.manager.api;

public class VerificationMethodNotAvailableException extends Exception {

    public VerificationMethodNotAvailableException() {
        super("Invalid verification method");
    }
}
