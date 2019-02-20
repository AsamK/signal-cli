package org.asamk.signal;

import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class JsonSendableMessage {
    public String getMessage() {
        return message;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    String message;
    List<String> attachments;
}
