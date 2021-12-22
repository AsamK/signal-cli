package org.asamk.signal.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ValueNode;

/**
 * Represents a JSON-RPC request.
 * https://www.jsonrpc.org/specification#request_object
 */
public final class JsonRpcRequest extends JsonRpcMessage {

    /**
     * A String specifying the version of the JSON-RPC protocol. MUST be exactly "2.0".
     */
    private String jsonrpc;

    /**
     * A String containing the name of the method to be invoked.
     * Method names that begin with the word rpc followed by a period character (U+002E or ASCII 46)
     * are reserved for rpc-internal methods and extensions and MUST NOT be used for anything else.
     */
    private String method;

    /**
     * A Structured value that holds the parameter values to be used during the invocation of the method.
     * This member MAY be omitted.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ContainerNode<?> params;

    /**
     * An identifier established by the Client that MUST contain a String, Number, or NULL value if included.
     * If it is not included it is assumed to be a notification.
     * The value SHOULD normally not be Null and Numbers SHOULD NOT contain fractional parts
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ValueNode id;

    public static JsonRpcRequest forNotification(
            final String method, final ContainerNode<?> params, final ValueNode id
    ) {
        return new JsonRpcRequest("2.0", method, params, id);
    }

    private JsonRpcRequest() {
    }

    private JsonRpcRequest(
            final String jsonrpc, final String method, final ContainerNode<?> params, final ValueNode id
    ) {
        this.jsonrpc = jsonrpc;
        this.method = method;
        this.params = params;
        this.id = id;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    public ContainerNode<?> getParams() {
        return params;
    }

    public ValueNode getId() {
        return id;
    }
}
