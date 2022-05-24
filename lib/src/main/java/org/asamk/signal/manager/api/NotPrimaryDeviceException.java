package org.asamk.signal.manager.api;

public class NotPrimaryDeviceException extends Exception {

    public NotPrimaryDeviceException() {
        super("This function is not supported for linked devices.");
    }
}
