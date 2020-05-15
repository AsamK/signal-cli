package org.asamk.signal.manager;

import org.whispersystems.util.Base64;

public class GroupNotFoundException extends Exception {

    public GroupNotFoundException(byte[] groupId) {
        super("Group not found: " + Base64.encodeBytes(groupId));
    }
}
