package org.asamk.signal.jsonrpc;

public class JsonRpcException extends Exception {

    private final JsonRpcResponse.Error error;

    public JsonRpcException(final JsonRpcResponse.Error error) {
        this.error = error;
    }

    public JsonRpcResponse.Error getError() {
        return error;
    }
}
