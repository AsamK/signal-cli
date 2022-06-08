package org.asamk.signal.jsonrpc;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.JsonRpcMultiCommand;
import org.asamk.signal.commands.JsonRpcRegistrationCommand;
import org.asamk.signal.commands.JsonRpcSingleCommand;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final Map<Integer, List<Pair<Manager, Manager.ReceiveMessageHandler>>> receiveHandlers = new HashMap<>();

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
            this.subscribeReceive(c.getManagers());
            c.addOnManagerAddedHandler(this::subscribeReceive);
            c.addOnManagerRemovedHandler(this::unsubscribeReceive);
        }

        handleConnection();
    }

    public void handleConnection(final Manager m) {
        this.m = m;

        if (!noReceiveOnStart) {
            subscribeReceive(m);
        }

        final var currentThread = Thread.currentThread();
        m.addClosedListener(currentThread::interrupt);

        handleConnection();
    }

    private static final AtomicInteger nextSubscriptionId = new AtomicInteger(0);

    private int subscribeReceive(final Manager manager) {
        return subscribeReceive(List.of(manager));
    }

    private int subscribeReceive(final List<Manager> managers) {
        final var subscriptionId = nextSubscriptionId.getAndIncrement();
        final var handlers = managers.stream().map(m -> {
            final var receiveMessageHandler = new JsonReceiveMessageHandler(m, s -> {
                final ContainerNode<?> params = objectMapper.valueToTree(s);
                ((ObjectNode) params).set("subscription", IntNode.valueOf(subscriptionId));
                final var jsonRpcRequest = JsonRpcRequest.forNotification("receive", params, null);
                try {
                    jsonRpcSender.sendRequest(jsonRpcRequest);
                } catch (AssertionError e) {
                    if (e.getCause() instanceof ClosedChannelException) {
                        unsubscribeReceive(subscriptionId);
                    }
                }
            });
            m.addReceiveHandler(receiveMessageHandler);
            return new Pair<>(m, (Manager.ReceiveMessageHandler) receiveMessageHandler);
        }).toList();
        receiveHandlers.put(subscriptionId, handlers);

        return subscriptionId;
    }

    private boolean unsubscribeReceive(final int subscriptionId) {
        final var handlers = receiveHandlers.remove(subscriptionId);
        if (handlers == null) {
            return false;
        }
        for (final var pair : handlers) {
            unsubscribeReceiveHandler(pair);
        }
        return true;
    }

    private void unsubscribeReceive(final Manager m) {
        final var subscriptionId = receiveHandlers.entrySet()
                .stream()
                .filter(e -> e.getValue().size() == 1 && e.getValue().get(0).first().equals(m))
                .map(Map.Entry::getKey)
                .findFirst();
        subscriptionId.ifPresent(this::unsubscribeReceive);
    }

    private void handleConnection() {
        try {
            jsonRpcReader.readMessages((method, params) -> handleRequest(objectMapper, method, params),
                    response -> logger.debug("Received unexpected response for id {}", response.getId()));
        } finally {
            receiveHandlers.forEach((_subscriptionId, handlers) -> handlers.forEach(this::unsubscribeReceiveHandler));
            receiveHandlers.clear();
        }
    }

    private void unsubscribeReceiveHandler(final Pair<Manager, Manager.ReceiveMessageHandler> pair) {
        final var m = pair.first();
        final var handler = pair.second();
        m.removeReceiveHandler(handler);
    }

    private JsonNode handleRequest(
            final ObjectMapper objectMapper, final String method, ContainerNode<?> params
    ) throws JsonRpcException {
        var command = getCommand(method);
        if (c != null) {
            if (command instanceof JsonRpcSingleCommand<?> jsonRpcCommand) {
                final var manager = getManagerFromParams(params);
                if (manager != null) {
                    return runCommand(objectMapper, params, new CommandRunnerImpl<>(manager, jsonRpcCommand));
                }
            }
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

    private Manager getManagerFromParams(final ContainerNode<?> params) throws JsonRpcException {
        if (params != null && params.hasNonNull("account")) {
            final var manager = c.getManager(params.get("account").asText());
            ((ObjectNode) params).remove("account");
            if (manager == null) {
                throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_PARAMS,
                        "Specified account does not exist",
                        null));
            }
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
            } catch (OverlappingFileLockException e) {
                logger.warn("Account is already in use");
                return null;
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
            throw new JsonRpcException(new JsonRpcResponse.Error(USER_ERROR,
                    e.getMessage(),
                    getErrorDataNode(objectMapper, result)));
        } catch (IOErrorException e) {
            throw new JsonRpcException(new JsonRpcResponse.Error(IO_ERROR,
                    e.getMessage(),
                    getErrorDataNode(objectMapper, result)));
        } catch (UntrustedKeyErrorException e) {
            throw new JsonRpcException(new JsonRpcResponse.Error(UNTRUSTED_KEY_ERROR,
                    e.getMessage(),
                    getErrorDataNode(objectMapper, result)));
        } catch (Throwable e) {
            logger.error("Command execution failed", e);
            throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INTERNAL_ERROR,
                    e.getMessage(),
                    getErrorDataNode(objectMapper, result)));
        }

        Object output = result[0] == null ? Map.of() : result[0];
        return objectMapper.valueToTree(output);
    }

    private JsonNode getErrorDataNode(final ObjectMapper objectMapper, final Object[] result) {
        if (result[0] == null) {
            return null;
        }
        return objectMapper.valueToTree(Map.of("response", result[0]));
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

    private class SubscribeReceiveCommand implements JsonRpcSingleCommand<Void>, JsonRpcMultiCommand<Void> {

        @Override
        public String getName() {
            return "subscribeReceive";
        }

        @Override
        public void handleCommand(
                final Void request, final Manager m, final JsonWriter jsonWriter
        ) throws CommandException {
            final var subscriptionId = subscribeReceive(m);
            jsonWriter.write(subscriptionId);
        }

        @Override
        public void handleCommand(
                final Void request, final MultiAccountManager c, final JsonWriter jsonWriter
        ) throws CommandException {
            final var subscriptionId = subscribeReceive(c.getManagers());
            jsonWriter.write(subscriptionId);
        }
    }

    private class UnsubscribeReceiveCommand implements JsonRpcSingleCommand<JsonNode>, JsonRpcMultiCommand<JsonNode> {

        @Override
        public String getName() {
            return "unsubscribeReceive";
        }

        @Override
        public TypeReference<JsonNode> getRequestType() {
            return new TypeReference<>() {};
        }

        @Override
        public void handleCommand(
                final JsonNode request, final Manager m, final JsonWriter jsonWriter
        ) throws CommandException {
            final var subscriptionId = getSubscriptionId(request);
            if (subscriptionId == null) {
                unsubscribeReceive(m);
            } else {
                if (!unsubscribeReceive(subscriptionId)) {
                    throw new UserErrorException("Unknown subscription id");
                }
            }
        }

        @Override
        public void handleCommand(
                final JsonNode request, final MultiAccountManager c, final JsonWriter jsonWriter
        ) throws CommandException {
            final var subscriptionId = getSubscriptionId(request);
            if (subscriptionId == null) {
                throw new UserErrorException("Missing subscription parameter with subscription id");
            } else {
                if (!unsubscribeReceive(subscriptionId)) {
                    throw new UserErrorException("Unknown subscription id");
                }
            }
        }

        private Integer getSubscriptionId(final JsonNode request) {
            if (request instanceof ArrayNode req) {
                return req.get(0).asInt();
            } else if (request instanceof ObjectNode req) {
                return req.get("subscription").asInt();
            } else {
                return null;
            }
        }
    }
}
