package org.asamk.signal;

import org.freedesktop.dbus.exceptions.DBusExecutionException;

public class GroupNotFoundException extends DBusExecutionException {

    public GroupNotFoundException(String message) {
        super(message);
    }

    public GroupNotFoundException(byte[] groupId) {
        super("Group not found: " + Base64.encodeBytes(groupId));
    }
}
