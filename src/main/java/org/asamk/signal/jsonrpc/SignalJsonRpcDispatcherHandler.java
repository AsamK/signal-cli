package org.asamk.signal.jsonrpc;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ContainerNode;

import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.JsonRpcCommand;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

public class SignalJsonRpcDispatcherHandler {

    private final static Logger logger = LoggerFactory.getLogger(SignalJsonRpcDispatcherHandler.class);

    private static final int USER_ERROR = -1;
    private static final int IO_ERROR = -3;
    private static final int UNTRUSTED_KEY_ERROR = -4;

    private final Manager m;
    private final JsonWriter outputWriter;
    private final Supplier<String> lineSupplier;

    public SignalJsonRpcDispatcherHandler(
            final Manager m, final JsonWriter outputWriter, final Supplier<String> lineSupplier
    ) {
        this.m = m;
        this.outputWriter = outputWriter;
        this.lineSupplier = lineSupplier;
    }

    public void handleConnection() {
        final var objectMapper = Util.createJsonObjectMapper();
        final var jsonRpcSender = new JsonRpcSender(outputWriter);

        final var receiveMessageHandler = new JsonReceiveMessageHandler(m,
                s -> jsonRpcSender.sendRequest(JsonRpcRequest.forNotification("receive",
                        objectMapper.valueToTree(s),
                        null)));
        try {
            m.addReceiveHandler(receiveMessageHandler);

            // Maybe this should be handled inside the Manager
            while (!m.hasCaughtUpWithOldMessages()) {
                try {
                    synchronized (m) {
                        m.wait();
                    }
                } catch (InterruptedException ignored) {
                }
            }

            final var jsonRpcReader = new JsonRpcReader(jsonRpcSender, lineSupplier);
            jsonRpcReader.readRequests((method, params) -> handleRequest(m, objectMapper, method, params),
                    response -> logger.debug("Received unexpected response for id {}", response.getId()));
        } finally {
            m.removeReceiveHandler(receiveMessageHandler);
        }
    }

    private JsonNode handleRequest(
            final Manager m, final ObjectMapper objectMapper, final String method, ContainerNode<?> params
    ) throws JsonRpcException {
        final Object[] result = {null};
        final JsonWriter commandOutputWriter = s -> {
            if (result[0] != null) {
                throw new AssertionError("Command may only write one json result");
            }

            result[0] = s;
        };

        var command = Commands.getCommand(method);
        if (!(command instanceof JsonRpcCommand)) {
            throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.METHOD_NOT_FOUND,
                    "Method not implemented",
                    null));
        }

        try {
            parseParamsAndRunCommand(m, objectMapper, params, commandOutputWriter, (JsonRpcCommand<?>) command);
        } catch (JsonMappingException e) {
            throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_REQUEST,
                    e.getMessage(),
                    null));
        } catch (UserErrorException e) {
            throw new JsonRpcException(new JsonRpcResponse.Error(USER_ERROR, e.getMessage(), null));
        } catch (IOErrorException e) {
            throw new JsonRpcException(new JsonRpcResponse.Error(IO_ERROR, e.getMessage(), null));
        } catch (UntrustedKeyErrorException e) {
            throw new JsonRpcException(new JsonRpcResponse.Error(UNTRUSTED_KEY_ERROR, e.getMessage(), null));
        } catch (Throwable e) {
            logger.error("Command execution failed", e);
            throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INTERNAL_ERROR,
                    e.getMessage(),
                    null));
        }

        Object output = result[0] == null ? Map.of() : result[0];
        return objectMapper.valueToTree(output);
    }

    private <T> void parseParamsAndRunCommand(
            final Manager m,
            final ObjectMapper objectMapper,
            final TreeNode params,
            final OutputWriter outputWriter,
            final JsonRpcCommand<T> command
    ) throws CommandException, JsonMappingException {
        T requestParams = null;
        final var requestType = command.getRequestType();
        if (params != null && requestType != null) {
            try {
                requestParams = objectMapper.readValue(objectMapper.treeAsTokens(params), requestType);
            } catch (JsonMappingException e) {
                throw e;
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        command.handleCommand(requestParams, m, outputWriter);
    }
}
