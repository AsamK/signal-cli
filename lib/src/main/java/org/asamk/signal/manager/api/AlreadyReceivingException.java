package org.asamk.signal.manager.api;

public class AlreadyReceivingException extends Exception {

    public AlreadyReceivingException(String message) {
        super(message);
    }

    public AlreadyReceivingException(String message, Exception e) {
        super(message, e);
    }
}
