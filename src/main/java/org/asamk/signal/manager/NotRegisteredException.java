package org.asamk.signal.manager;

public class NotRegisteredException extends Exception {

    public NotRegisteredException() {
        super("User is not registered.");
    }
}
