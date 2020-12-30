package org.asamk.signal.manager;

public class GroupIdFormatException extends Exception {

    public GroupIdFormatException(String groupId, Throwable e) {
        super("Failed to decode groupId (must be base64) \"" + groupId + "\": " + e.getMessage(), e);
    }
}
