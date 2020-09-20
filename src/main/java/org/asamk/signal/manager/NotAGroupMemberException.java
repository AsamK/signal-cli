package org.asamk.signal.manager;

import org.whispersystems.util.Base64;

public class NotAGroupMemberException extends Exception {

    public NotAGroupMemberException(byte[] groupId, String groupName) {
        super("User is not a member in group: " + groupName + " (" + Base64.encodeBytes(groupId) + ")");
    }
}
