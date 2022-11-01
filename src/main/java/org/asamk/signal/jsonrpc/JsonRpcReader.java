package org.asamk.signal.jsonrpc;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;

import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

public class JsonRpcReader {

    private final static Logger logger = LoggerFactory.getLogger(JsonRpcReader.class);

    private final JsonRpcSender jsonRpcSender;
    private final ObjectMapper objectMapper;
    private final InputStream input;
    private final Supplier<String> lineSupplier;

    public JsonRpcReader(final JsonRpcSender jsonRpcSender, final Supplier<String> lineSupplier) {
        this.jsonRpcSender = jsonRpcSender;
        this.input = null;
        this.lineSupplier = lineSupplier;
        this.objectMapper = Util.createJsonObjectMapper();
    }

    public JsonRpcReader(final JsonRpcSender jsonRpcSender, final InputStream input) {
        this.jsonRpcSender = jsonRpcSender;
        this.input = input;
        this.lineSupplier = null;
        this.objectMapper = Util.createJsonObjectMapper();
    }

    public void readMessages(final RequestHandler requestHandler, final Consumer<JsonRpcResponse> responseHandler) {
        if (input != null) {
            JsonRpcMessage message = parseJsonRpcMessage(input);
            if (message == null) {
                return;
            }

            handleMessage(message, requestHandler, responseHandler);
            return;
        }

        while (!Thread.interrupted()) {
            String input = lineSupplier.get();
            if (input == null) {
                logger.trace("Reached end of JSON-RPC input stream.");
                break;
            }

            logger.trace("Incoming JSON-RPC message: {}", input);
            JsonRpcMessage message = parseJsonRpcMessage(input);
            if (message == null) {
                continue;
            }

            handleMessage(message, requestHandler, responseHandler);
        }
    }

    private void handleMessage(
            final JsonRpcMessage message,
            final RequestHandler requestHandler,
            final Consumer<JsonRpcResponse> responseHandler
    ) {
        if (message instanceof final JsonRpcRequest jsonRpcRequest) {
            logger.debug("Received json rpc request, method: " + jsonRpcRequest.getMethod());
            final var response = handleRequest(requestHandler, jsonRpcRequest);
            if (response != null) {
                jsonRpcSender.sendResponse(response);
            }
        } else if (message instanceof JsonRpcResponse jsonRpcResponse) {
            responseHandler.accept(jsonRpcResponse);
        } else {
            final var responseList = ((JsonRpcBatchMessage) message).getMessages().stream().map(jsonNode -> {
                final JsonRpcRequest request;
                try {
                    request = parseJsonRpcRequest(jsonNode);
                } catch (JsonRpcException e) {
                    return JsonRpcResponse.forError(e.getError(), getId(jsonNode));
                }

                return handleRequest(requestHandler, request);
            }).filter(Objects::nonNull).toList();

            jsonRpcSender.sendBatchResponses(responseList);
        }
    }

    private JsonRpcResponse handleRequest(final RequestHandler requestHandler, final JsonRpcRequest request) {
        try {
            final var result = requestHandler.apply(request.getMethod(), request.getParams());
            if (request.getId() != null) {
                return JsonRpcResponse.forSuccess(result, request.getId());
            } else {
                logger.debug("Command '{}' succeeded but client didn't specify an id, dropping response",
                        request.getMethod());
            }
        } catch (JsonRpcException e) {
            if (request.getId() != null) {
                return JsonRpcResponse.forError(e.getError(), request.getId());
            } else {
                logger.debug("Command '{}' failed but client didn't specify an id, dropping error: {}",
                        request.getMethod(),
                        e.getMessage());
            }
        }
        return null;
    }

    private JsonRpcMessage parseJsonRpcMessage(final String input) {
        final JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(input);
        } catch (JsonParseException e) {
            jsonRpcSender.sendResponse(JsonRpcResponse.forError(new JsonRpcResponse.Error(JsonRpcResponse.Error.PARSE_ERROR,
                    e.getMessage(),
                    null), null));
            return null;
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        return parseJsonRpcMessage(jsonNode);
    }

    private JsonRpcMessage parseJsonRpcMessage(final InputStream input) {
        final JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(input);
        } catch (JsonParseException e) {
            jsonRpcSender.sendResponse(JsonRpcResponse.forError(new JsonRpcResponse.Error(JsonRpcResponse.Error.PARSE_ERROR,
                    e.getMessage(),
                    null), null));
            return null;
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        return parseJsonRpcMessage(jsonNode);
    }

    private JsonRpcMessage parseJsonRpcMessage(final JsonNode jsonNode) {
        if (jsonNode == null) {
            jsonRpcSender.sendResponse(JsonRpcResponse.forError(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_REQUEST,
                    "invalid request",
                    null), null));
            return null;
        } else if (jsonNode.isArray()) {
            if (jsonNode.size() == 0) {
                jsonRpcSender.sendResponse(JsonRpcResponse.forError(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_REQUEST,
                        "invalid request",
                        null), null));
                return null;
            }
            return new JsonRpcBatchMessage(StreamSupport.stream(jsonNode.spliterator(), false).toList());
        } else if (jsonNode.isObject()) {
            if (jsonNode.has("result") || jsonNode.has("error")) {
                return parseJsonRpcResponse(jsonNode);
            } else {
                try {
                    return parseJsonRpcRequest(jsonNode);
                } catch (JsonRpcException e) {
                    jsonRpcSender.sendResponse(JsonRpcResponse.forError(e.getError(), getId(jsonNode)));
                    return null;
                }
            }
        } else {
            jsonRpcSender.sendResponse(JsonRpcResponse.forError(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_REQUEST,
                    "unexpected type: " + jsonNode.getNodeType().name(),
                    null), null));
            return null;
        }
    }

    private ValueNode getId(JsonNode jsonNode) {
        final var id = jsonNode.get("id");
        return id instanceof ValueNode ? (ValueNode) id : null;
    }

    private JsonRpcRequest parseJsonRpcRequest(final JsonNode input) throws JsonRpcException {
        if (input instanceof ObjectNode i && input.has("params") && input.get("params").isNull()) {
            // Workaround for clients that send a null params field instead of omitting it
            i.remove("params");
        }
        JsonRpcRequest request;
        try {
            request = objectMapper.treeToValue(input, JsonRpcRequest.class);
        } catch (JsonMappingException e) {
            throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_REQUEST,
                    e.getMessage(),
                    null));
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        if (!"2.0".equals(request.getJsonrpc())) {
            throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_REQUEST,
                    "only jsonrpc version 2.0 is supported",
                    null));
        }

        if (request.getMethod() == null) {
            throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_REQUEST,
                    "method field must be set",
                    null));
        }

        return request;
    }

    private JsonRpcResponse parseJsonRpcResponse(final JsonNode input) {
        JsonRpcResponse response;
        try {
            response = objectMapper.treeToValue(input, JsonRpcResponse.class);
        } catch (JsonParseException | JsonMappingException e) {
            logger.debug("Received invalid jsonrpc response {}", e.getMessage());
            return null;
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        if (!"2.0".equals(response.getJsonrpc())) {
            logger.debug("Received invalid jsonrpc response with invalid version {}", response.getJsonrpc());
            return null;
        }

        if (response.getResult() != null && response.getError() != null) {
            logger.debug("Received invalid jsonrpc response with both result and error");
            return null;
        }

        if (response.getResult() == null && response.getError() == null) {
            logger.debug("Received invalid jsonrpc response without result and error");
            return null;
        }

        if (response.getId() == null || response.getId().isNull()) {
            logger.debug("Received invalid jsonrpc response without id");
            return null;
        }

        return response;
    }

    public interface RequestHandler {

        JsonNode apply(String method, ContainerNode<?> params) throws JsonRpcException;
    }
}
