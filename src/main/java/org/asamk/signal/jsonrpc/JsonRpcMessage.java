package org.asamk.signal.jsonrpc;

/**
 * Represents a JSON-RPC (batch) request or (batch) response.
 * <a href="https://www.jsonrpc.org/specification">https://www.jsonrpc.org/specification</a>
 */
public sealed abstract class JsonRpcMessage permits JsonRpcBatchMessage, JsonRpcRequest, JsonRpcResponse {}
