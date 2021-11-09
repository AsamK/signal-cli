package org.asamk.signal.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public final class JsonRpcBatchMessage extends JsonRpcMessage {

    List<JsonNode> messages;

    public JsonRpcBatchMessage(final List<JsonNode> messages) {
        this.messages = messages;
    }

    public List<JsonNode> getMessages() {
        return messages;
    }
}
