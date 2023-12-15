package org.asamk.signal.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ValueNode;

/**
 * Represents a JSON-RPC response.
 * <a href="https://www.jsonrpc.org/specification#response_object">https://www.jsonrpc.org/specification#response_object</a>
 */
public final class JsonRpcResponse extends JsonRpcMessage {

    /**
     * A String specifying the version of the JSON-RPC protocol. MUST be exactly "2.0".
     */
    String jsonrpc;

    /**
     * This member is REQUIRED on success.
     * This member MUST NOT exist if there was an error invoking the method.
     * The value of this member is determined by the method invoked on the Server.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonNode result;

    /**
     * This member is REQUIRED on error.
     * This member MUST NOT exist if there was no error triggered during invocation.
     * The value for this member MUST be an Object as defined in section 5.1.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Error error;

    /**
     * This member is REQUIRED.
     * It MUST be the same as the value of the id member in the Request Object.
     * If there was an error in detecting the id in the Request object (e.g. Parse error/Invalid Request), it MUST be Null.
     */
    ValueNode id;

    public static JsonRpcResponse forSuccess(JsonNode result, ValueNode id) {
        return new JsonRpcResponse("2.0", result, null, id);
    }

    public static JsonRpcResponse forError(Error error, ValueNode id) {
        return new JsonRpcResponse("2.0", null, error, id);
    }

    private JsonRpcResponse() {
    }

    private JsonRpcResponse(final String jsonrpc, final JsonNode result, final Error error, final ValueNode id) {
        this.jsonrpc = jsonrpc;
        this.result = result;
        this.error = error;
        this.id = id;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public JsonNode getResult() {
        return result;
    }

    public Error getError() {
        return error;
    }

    public ValueNode getId() {
        return id;
    }

    public static class Error {

        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;

        /**
         * A Number that indicates the error type that occurred.
         * This MUST be an integer.
         */
        int code;

        /**
         * A String providing a short description of the error.
         * The message SHOULD be limited to a concise single sentence.
         */
        String message;

        /**
         * A Primitive or Structured value that contains additional information about the error.
         * This may be omitted.
         * The value of this member is defined by the Server (e.g. detailed error information, nested errors etc.).
         */
        JsonNode data;

        public Error(final int code, final String message, final JsonNode data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public JsonNode getData() {
            return data;
        }
    }
}
