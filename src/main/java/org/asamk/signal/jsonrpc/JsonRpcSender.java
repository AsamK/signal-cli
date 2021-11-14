package org.asamk.signal.jsonrpc;

import org.asamk.signal.output.JsonWriter;

import java.util.List;

public class JsonRpcSender {

    private final JsonWriter jsonWriter;

    public JsonRpcSender(final JsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    public void sendRequest(JsonRpcRequest request) {
        jsonWriter.write(request);
    }

    public void sendBatchRequests(List<JsonRpcRequest> requests) {
        jsonWriter.write(requests);
    }

    public void sendResponse(JsonRpcResponse response) {
        jsonWriter.write(response);
    }

    public void sendBatchResponses(List<JsonRpcResponse> responses) {
        jsonWriter.write(responses);
    }
}
