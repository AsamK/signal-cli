package org.asamk.signal.manager.groups;

public class GroupNotFoundException extends Exception {

    public GroupNotFoundException(GroupId groupId) {
        super("Group not found: " + groupId.toBase64());
    }
}
