package org.asamk.signal;

import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.signalservice.internal.util.Base64;

public class NotAGroupMemberException extends DBusExecutionException {

    public NotAGroupMemberException(byte[] groupId, String groupName) {
        super("User is not a member in group: " + groupName + " (" + Base64.encodeBytes(groupId) + ")");
    }
}
