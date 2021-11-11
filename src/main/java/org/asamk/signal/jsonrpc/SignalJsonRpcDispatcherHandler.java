package org.asamk.signal.jsonrpc;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.JsonRpcMultiCommand;
import org.asamk.signal.commands.JsonRpcRegistrationCommand;
import org.asamk.signal.commands.JsonRpcSingleCommand;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class SignalJsonRpcDispatcherHandler {

    private final static Logger logger = LoggerFactory.getLogger(SignalJsonRpcDispatcherHandler.class);

    private static final int USER_ERROR = -1;
    private static final int IO_ERROR = -3;
    private static final int UNTRUSTED_KEY_ERROR = -4;

    private final ObjectMapper objectMapper;
    private final JsonRpcSender jsonRpcSender;
    private final JsonRpcReader jsonRpcReader;
    private final boolean noReceiveOnStart;

    private MultiAccountManager c;
    private final Map<Manager, Manager.ReceiveMessageHandler> receiveHandlers = new HashMap<>();

    private Manager m;

    public SignalJsonRpcDispatcherHandler(
            final JsonWriter jsonWriter, final Supplier<String> lineSupplier, final boolean noReceiveOnStart
    ) {
        this.noReceiveOnStart = noReceiveOnStart;
        this.objectMapper = Util.createJsonObjectMapper();
        this.jsonRpcSender = new JsonRpcSender(jsonWriter);
        this.jsonRpcReader = new JsonRpcReader(jsonRpcSender, lineSupplier);
    }

    public void handleConnection(final MultiAccountManager c) {
        this.c = c;

        if (!noReceiveOnStart) {
            c.getAccountNumbers().stream().map(c::getManager).filter(Objects::nonNull).forEach(this::subscribeReceive);
        }

        handleConnection();
    }

    public void handleConnection(final Manager m) {
        this.m = m;

        if (!noReceiveOnStart) {
            subscribeReceive(m);
        }

        handleConnection();
    }

    private void subscribeReceive(final Manager m) {
        if (receiveHandlers.containsKey(m)) {
            return;
        }

        final var receiveMessageHandler = new JsonReceiveMessageHandler(m,
                s -> jsonRpcSender.sendRequest(JsonRpcRequest.forNotification("receive",
                        objectMapper.valueToTree(s),
                        null)));
        m.addReceiveHandler(receiveMessageHandler);
        receiveHandlers.put(m, receiveMessageHandler);

        while (!m.hasCaughtUpWithOldMessages()) {
            try {
                synchronized (m) {
                    m.wait();
                }
            } catch (InterruptedException ignored) {
            }
        }
    }

    void unsubscribeReceive(final Manager m) {
        final var receiveMessageHandler = receiveHandlers.remove(m);
        if (receiveMessageHandler != null) {
            m.removeReceiveHandler(receiveMessageHandler);
        }
    }

    private void handleConnection() {
        try {
            jsonRpcReader.readMessages((method, params) -> handleRequest(objectMapper, method, params),
                    response -> logger.debug("Received unexpected response for id {}", response.getId()));
        } finally {
            receiveHandlers.forEach(Manager::removeReceiveHandler);
            receiveHandlers.clear();
        }
    }

    private JsonNode handleRequest(
            final ObjectMapper objectMapper, final String method, ContainerNode<?> params
    ) throws JsonRpcException {
        var command = getCommand(method);
        // TODO implement link
        if (c != null) {
            if (command instanceof JsonRpcMultiCommand<?> jsonRpcCommand) {
                return runCommand(objectMapper, params, new MultiCommandRunnerImpl<>(c, jsonRpcCommand));
            }
            if (command instanceof JsonRpcRegistrationCommand<?> jsonRpcCommand) {
                try (var manager = getRegistrationManagerFromParams(params)) {
                    if (manager != null) {
                        return runCommand(objectMapper,
                                params,
                                new RegistrationCommandRunnerImpl<>(manager, c, jsonRpcCommand));
                    } else {
                        throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_PARAMS,
                                "Method requires valid account parameter",
                                null));
                    }
                } catch (IOException e) {
                    logger.warn("Failed to close registration manager", e);
                }
            }
        }
        if (command instanceof JsonRpcSingleCommand<?> jsonRpcCommand) {
            if (m != null) {
                return runCommand(objectMapper, params, new CommandRunnerImpl<>(m, jsonRpcCommand));
            }

            final var manager = getManagerFromParams(params);
            if (manager != null) {
                return runCommand(objectMapper, params, new CommandRunnerImpl<>(manager, jsonRpcCommand));
            } else {
                throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_PARAMS,
                        "Method requires valid account parameter",
                        null));
            }
        }

        throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.METHOD_NOT_FOUND,
                "Method not implemented",
                null));
    }

    private Manager getManagerFromParams(final ContainerNode<?> params) {
        if (params != null && params.has("account")) {
            final var manager = c.getManager(params.get("account").asText());
            ((ObjectNode) params).remove("account");
            return manager;
        }
        return null;
    }

    private RegistrationManager getRegistrationManagerFromParams(final ContainerNode<?> params) {
        if (params != null && params.has("account")) {
            try {
                final var registrationManager = c.getNewRegistrationManager(params.get("account").asText());
                ((ObjectNode) params).remove("account");
                return registrationManager;
            } catch (IOException | IllegalStateException e) {
                logger.warn("Failed to load registration manager", e);
                return null;
            }
        }
        return null;
    }

    private Command getCommand(final String method) {
        if ("subscribeReceive".equals(method)) {
            return new SubscribeReceiveCommand();
        }
        if ("unsubscribeReceive".equals(method)) {
            return new UnsubscribeReceiveCommand();
        }
        return Commands.getCommand(method);
    }

    private record CommandRunnerImpl<T>(Manager m, JsonRpcSingleCommand<T> command) implements CommandRunner<T> {

        @Override
        public void handleCommand(final T request, final JsonWriter jsonWriter) throws CommandException {
            command.handleCommand(request, m, jsonWriter);
        }

        @Override
        public TypeReference<T> getRequestType() {
            return command.getRequestType();
        }
    }

    private record RegistrationCommandRunnerImpl<T>(
            RegistrationManager m, MultiAccountManager c, JsonRpcRegistrationCommand<T> command
    ) implements CommandRunner<T> {

        @Override
        public void handleCommand(final T request, final JsonWriter jsonWriter) throws CommandException {
            command.handleCommand(request, m, jsonWriter);
        }

        @Override
        public TypeReference<T> getRequestType() {
            return command.getRequestType();
        }
    }

    private record MultiCommandRunnerImpl<T>(
            MultiAccountManager c, JsonRpcMultiCommand<T> command
    ) implements CommandRunner<T> {

        @Override
        public void handleCommand(final T request, final JsonWriter jsonWriter) throws CommandException {
            command.handleCommand(request, c, jsonWriter);
        }

        @Override
        public TypeReference<T> getRequestType() {
            return command.getRequestType();
        }
    }

    interface CommandRunner<T> {

        void handleCommand(T request, JsonWriter jsonWriter) throws CommandException;

        TypeReference<T> getRequestType();
    }

    private JsonNode runCommand(
            final ObjectMapper objectMapper, final ContainerNode<?> params, final CommandRunner<?> command
    ) throws JsonRpcException {
        final Object[] result = {null};
        final JsonWriter commandJsonWriter = s -> {
            if (result[0] != null) {
                throw new AssertionError("Command may only write one json result");
            }

            result[0] = s;
        };

        try {
            parseParamsAndRunCommand(objectMapper, params, commandJsonWriter, command);
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
            final ObjectMapper objectMapper,
            final TreeNode params,
            final JsonWriter jsonWriter,
            final CommandRunner<T> command
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
        command.handleCommand(requestParams, jsonWriter);
    }

    private class SubscribeReceiveCommand implements JsonRpcSingleCommand<Void> {

        @Override
        public String getName() {
            return "subscribeReceive";
        }

        @Override
        public void handleCommand(
                final Void request, final Manager m, final JsonWriter jsonWriter
        ) throws CommandException {
            subscribeReceive(m);
        }
    }

    private class UnsubscribeReceiveCommand implements JsonRpcSingleCommand<Void> {

        @Override
        public String getName() {
            return "unsubscribeReceive";
        }

        @Override
        public void handleCommand(
                final Void request, final Manager m, final JsonWriter jsonWriter
        ) throws CommandException {
            unsubscribeReceive(m);
        }
    }
}
