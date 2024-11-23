package org.asamk.signal.manager.storage.recipients;

public class InvalidAddress extends AssertionError {

    InvalidAddress(String message) {
        super(message);
    }
}
