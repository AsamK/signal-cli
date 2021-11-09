package org.asamk.signal.jsonrpc;

/**
 * Represents a JSON-RPC (batch) request or (batch) response.
 * https://www.jsonrpc.org/specification
 */
public sealed abstract class JsonRpcMessage permits JsonRpcBatchMessage, JsonRpcRequest, JsonRpcResponse {

}
