package org.asamk.signal;

import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.signalservice.internal.util.Base64;

public class GroupNotFoundException extends DBusExecutionException {

    public GroupNotFoundException(byte[] groupId) {
        super("Group not found: " + Base64.encodeBytes(groupId));
    }
}
