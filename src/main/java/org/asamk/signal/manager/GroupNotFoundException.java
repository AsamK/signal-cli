package org.asamk.signal.manager;

public class GroupNotFoundException extends Exception {

    public GroupNotFoundException(GroupId groupId) {
        super("Group not found: " + groupId.toBase64());
    }
}
