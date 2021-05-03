package org.asamk.signal.manager;

public class NotMasterDeviceException extends Exception {

    public NotMasterDeviceException() {
        super("This function is not supported for linked devices.");
    }
}
