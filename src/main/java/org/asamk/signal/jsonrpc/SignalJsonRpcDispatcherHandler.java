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
import org.asamk.signal.json.JsonCallEvent;
import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class SignalJsonRpcDispatcherHandler {

    private static final Logger logger = LoggerFactory.getLogger(SignalJsonRpcDispatcherHandler.class);

    private final ObjectMapper objectMapper;
    private final JsonRpcSender jsonRpcSender;
    private final JsonRpcReader jsonRpcReader;
    private final boolean noReceiveOnStart;

    private final Map<Integer, List<Pair<Manager, Manager.ReceiveMessageHandler>>> receiveHandlers = new HashMap<>();
    private final Map<Integer, List<Pair<Manager, Manager.CallEventListener>>> callEventHandlers = new HashMap<>();
    private SignalJsonRpcCommandHandler commandHandler;

    public SignalJsonRpcDispatcherHandler(
            final JsonWriter jsonWriter,
            final Supplier<String> lineSupplier,
            final boolean noReceiveOnStart
    ) {
        this.noReceiveOnStart = noReceiveOnStart;
        this.objectMapper = Util.createJsonObjectMapper();
        this.jsonRpcSender = new JsonRpcSender(jsonWriter);
        this.jsonRpcReader = new JsonRpcReader(jsonRpcSender, lineSupplier);
    }

    public void handleConnection(final MultiAccountManager c) {
        this.commandHandler = new SignalJsonRpcCommandHandler(c, this::getCommand);

        if (!noReceiveOnStart) {
            this.subscribeReceive(c.getManagers(), true);
            c.addOnManagerAddedHandler(m -> subscribeReceive(m, true));
            c.addOnManagerRemovedHandler(this::unsubscribeReceive);
        }
        c.addOnManagerAddedHandler(m -> receiveHandlers.forEach((subscriptionId, handlers) -> handlers.add(
                createReceiveHandler(m, subscriptionId, false))));
        c.addOnManagerAddedHandler(m -> callEventHandlers.forEach((subscriptionId, handlers) -> handlers.add(
                createCallEventHandler(m, subscriptionId))));

        handleConnection();
    }

    public void handleConnection(final Manager m) {
        this.commandHandler = new SignalJsonRpcCommandHandler(m, this::getCommand);

        if (!noReceiveOnStart) {
            subscribeReceive(m, true);
        }

        final var currentThread = Thread.currentThread();
        m.addClosedListener(currentThread::interrupt);

        handleConnection();
    }

    private int subscribeCallEvents(final Manager manager) {
        return subscribeCallEvents(List.of(manager));
    }

    private int subscribeCallEvents(final Collection<Manager> managers) {
        final var subscriptionId = nextSubscriptionId.getAndIncrement();
        final var listeners = managers.stream().map(m -> createCallEventHandler(m, subscriptionId)).toList();
        callEventHandlers.put(subscriptionId, listeners);
        return subscriptionId;
    }

    private Pair<Manager, Manager.CallEventListener> createCallEventHandler(final Manager m, final int subscriptionId) {
        final Manager.CallEventListener listener = (callInfo, reason) -> {
            final var params = new ObjectNode(objectMapper.getNodeFactory());
            params.set("subscription", IntNode.valueOf(subscriptionId));
            params.set("result", objectMapper.valueToTree(JsonCallEvent.from(callInfo, reason)));
            final var jsonRpcRequest = JsonRpcRequest.forNotification("callEvent", params, null);
            try {
                jsonRpcSender.sendRequest(jsonRpcRequest);
            } catch (AssertionError e) {
                if (e.getCause() instanceof ClosedChannelException) {
                    unsubscribeReceive(subscriptionId);
                }
            }
        };
        m.addCallEventListener(listener);
        return new Pair<>(m, listener);
    }

    private boolean unsubscribeCallEvents(final int subscriptionId) {
        final var handlers = callEventHandlers.remove(subscriptionId);
        if (handlers == null) {
            return false;
        }
        for (final var pair : handlers) {
            unsubscribeCallEventHandler(pair);
        }
        return true;
    }

    private void unsubscribeAllCallEvents() {
        callEventHandlers.forEach((_subscriptionId, handlers) -> handlers.forEach(this::unsubscribeCallEventHandler));
        callEventHandlers.clear();
    }

    private void unsubscribeCallEventHandler(final Pair<Manager, Manager.CallEventListener> pair) {
        final var m = pair.first();
        final var handler = pair.second();
        m.removeCallEventListener(handler);
    }

    private static final AtomicInteger nextSubscriptionId = new AtomicInteger(0);

    private int subscribeReceive(final Manager manager, boolean internalSubscription) {
        return subscribeReceive(List.of(manager), internalSubscription);
    }

    private int subscribeReceive(final List<Manager> managers, boolean internalSubscription) {
        final var subscriptionId = nextSubscriptionId.getAndIncrement();
        final var handlers = managers.stream()
                .map(m -> createReceiveHandler(m, subscriptionId, internalSubscription))
                .toList();
        receiveHandlers.put(subscriptionId, handlers);

        return subscriptionId;
    }

    private Pair<Manager, Manager.ReceiveMessageHandler> createReceiveHandler(
            final Manager m,
            final int subscriptionId,
            final boolean internalSubscription
    ) {
        final var receiveMessageHandler = new JsonReceiveMessageHandler(m, s -> {
            ContainerNode<?> params;
            if (internalSubscription) {
                params = objectMapper.valueToTree(s);
            } else {
                final var paramsNode = new ObjectNode(objectMapper.getNodeFactory());
                paramsNode.set("subscription", IntNode.valueOf(subscriptionId));
                paramsNode.set("result", objectMapper.valueToTree(s));
                params = paramsNode;
            }
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
        return new Pair<>(m, receiveMessageHandler);
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
                .filter(e -> e.getValue().size() == 1 && e.getValue().getFirst().first().equals(m))
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
            unsubscribeAllCallEvents();
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
        if ("subscribeCallEvents".equals(method)) {
            return new SubscribeCallEventsCommand();
        }
        if ("unsubscribeCallEvents".equals(method)) {
            return new UnsubscribeCallEventsCommand();
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
                final Void request,
                final Manager m,
                final JsonWriter jsonWriter
        ) throws CommandException {
            final var subscriptionId = subscribeReceive(m, false);
            jsonWriter.write(subscriptionId);
        }

        @Override
        public void handleCommand(
                final Void request,
                final MultiAccountManager c,
                final JsonWriter jsonWriter
        ) throws CommandException {
            final var subscriptionId = subscribeReceive(c.getManagers(), false);
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
                final JsonNode request,
                final Manager m,
                final JsonWriter jsonWriter
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
                final JsonNode request,
                final MultiAccountManager c,
                final JsonWriter jsonWriter
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
            return switch (request) {
                case ArrayNode req -> req.get(0).asInt();
                case ObjectNode req -> req.get("subscription").asInt();
                case null, default -> null;
            };
        }
    }

    private class SubscribeCallEventsCommand implements JsonRpcSingleCommand<Void>, JsonRpcMultiCommand<Void> {

        @Override
        public String getName() {
            return "subscribeCallEvents";
        }

        @Override
        public void handleCommand(
                final Void request,
                final Manager m,
                final JsonWriter jsonWriter
        ) throws CommandException {
            final var subscriptionId = subscribeCallEvents(m);
            jsonWriter.write(subscriptionId);
        }

        @Override
        public void handleCommand(
                final Void request,
                final MultiAccountManager c,
                final JsonWriter jsonWriter
        ) throws CommandException {
            final var subscriptionId = subscribeCallEvents(c.getManagers());
            jsonWriter.write(subscriptionId);
        }
    }

    private class UnsubscribeCallEventsCommand implements JsonRpcSingleCommand<JsonNode>, JsonRpcMultiCommand<JsonNode> {

        @Override
        public String getName() {
            return "unsubscribeCallEvents";
        }

        @Override
        public TypeReference<JsonNode> getRequestType() {
            return new TypeReference<>() {};
        }

        @Override
        public void handleCommand(
                final JsonNode request,
                final Manager m,
                final JsonWriter jsonWriter
        ) throws CommandException {
            final var subscriptionId = getSubscriptionId(request);
            if (subscriptionId == null) {
                throw new UserErrorException("Missing subscription parameter with subscription id");
            } else {
                if (!unsubscribeCallEvents(subscriptionId)) {
                    throw new UserErrorException("Unknown subscription id");
                }
            }
        }

        @Override
        public void handleCommand(
                final JsonNode request,
                final MultiAccountManager c,
                final JsonWriter jsonWriter
        ) throws CommandException {
            final var subscriptionId = getSubscriptionId(request);
            if (subscriptionId == null) {
                throw new UserErrorException("Missing subscription parameter with subscription id");
            } else {
                if (!unsubscribeCallEvents(subscriptionId)) {
                    throw new UserErrorException("Unknown subscription id");
                }
            }
        }

        private Integer getSubscriptionId(final JsonNode request) {
            return switch (request) {
                case ArrayNode req -> req.get(0).asInt();
                case ObjectNode req -> req.get("subscription").asInt();
                case null, default -> null;
            };
        }
    }
}
