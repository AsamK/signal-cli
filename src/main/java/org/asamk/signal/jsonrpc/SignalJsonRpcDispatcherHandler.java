package org.asamk.signal.jsonrpc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.JsonRpcMultiCommand;
import org.asamk.signal.commands.JsonRpcSingleCommand;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class SignalJsonRpcDispatcherHandler {

    private final static Logger logger = LoggerFactory.getLogger(SignalJsonRpcDispatcherHandler.class);

    private final ObjectMapper objectMapper;
    private final JsonRpcSender jsonRpcSender;
    private final JsonRpcReader jsonRpcReader;
    private final boolean noReceiveOnStart;

    private final Map<Integer, List<Pair<Manager, Manager.ReceiveMessageHandler>>> receiveHandlers = new HashMap<>();
    private SignalJsonRpcCommandHandler commandHandler;

    public SignalJsonRpcDispatcherHandler(
            final JsonWriter jsonWriter, final Supplier<String> lineSupplier, final boolean noReceiveOnStart
    ) {
        this.noReceiveOnStart = noReceiveOnStart;
        this.objectMapper = Util.createJsonObjectMapper();
        this.jsonRpcSender = new JsonRpcSender(jsonWriter);
        this.jsonRpcReader = new JsonRpcReader(jsonRpcSender, lineSupplier);
    }

    public void handleConnection(final MultiAccountManager c) {
        this.commandHandler = new SignalJsonRpcCommandHandler(c, this::getCommand);

        if (!noReceiveOnStart) {
            this.subscribeReceive(c.getManagers());
            c.addOnManagerAddedHandler(this::subscribeReceive);
            c.addOnManagerRemovedHandler(this::unsubscribeReceive);
        }

        handleConnection();
    }

    public void handleConnection(final Manager m) {
        this.commandHandler = new SignalJsonRpcCommandHandler(m, this::getCommand);

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
            jsonRpcReader.readMessages((method, params) -> commandHandler.handleRequest(objectMapper, method, params),
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

    private Command getCommand(final String method) {
        if ("subscribeReceive".equals(method)) {
            return new SubscribeReceiveCommand();
        }
        if ("unsubscribeReceive".equals(method)) {
            return new UnsubscribeReceiveCommand();
        }
        return Commands.getCommand(method);
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
