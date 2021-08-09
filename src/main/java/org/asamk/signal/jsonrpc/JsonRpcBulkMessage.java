package org.asamk.signal.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class JsonRpcBulkMessage extends JsonRpcMessage {

    List<JsonNode> messages;

    public JsonRpcBulkMessage(final List<JsonNode> messages) {
        this.messages = messages;
    }

    public List<JsonNode> getMessages() {
        return messages;
    }
}
