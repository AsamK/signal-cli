package org.asamk.signal.manager.api;

public class GroupNotFoundException extends Exception {

    public GroupNotFoundException(GroupId groupId) {
        super("Group not found: " + groupId.toBase64());
    }
}
