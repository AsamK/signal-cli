package org.asamk.signal.manager;

public class NotAGroupMemberException extends Exception {

    public NotAGroupMemberException(GroupId groupId, String groupName) {
        super("User is not a member in group: " + groupName + " (" + groupId.toBase64() + ")");
    }
}
