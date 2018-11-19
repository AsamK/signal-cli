package org.asamk.signal;

import java.io.IOException;

public class GroupIdFormatException extends Exception {

    public GroupIdFormatException(String groupId, IOException e) {
        super("Failed to decode groupId (must be base64) \"" + groupId + "\": " + e.getMessage());
    }
}
