package org.asamk.signal.jsonrpc;

/**
 * Represents a JSON-RPC (bulk) request or (bulk) response.
 * https://www.jsonrpc.org/specification
 */
public sealed abstract class JsonRpcMessage permits JsonRpcBulkMessage, JsonRpcRequest, JsonRpcResponse {

}
