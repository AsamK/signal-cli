package org.asamk.signal.jsonrpc;

import org.asamk.signal.JsonWriter;

import java.util.List;

public class JsonRpcSender {

    private final JsonWriter jsonWriter;

    public JsonRpcSender(final JsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    public void sendRequest(JsonRpcRequest request) {
        jsonWriter.write(request);
    }

    public void sendBulkRequests(List<JsonRpcRequest> requests) {
        jsonWriter.write(requests);
    }

    public void sendResponse(JsonRpcResponse response) {
        jsonWriter.write(response);
    }

    public void sendBulkResponses(List<JsonRpcResponse> responses) {
        jsonWriter.write(responses);
    }
}
