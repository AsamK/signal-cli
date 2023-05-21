package org.asamk.signal.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.asamk.signal.commands.Commands;
import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.jsonrpc.JsonRpcReader;
import org.asamk.signal.jsonrpc.JsonRpcResponse;
import org.asamk.signal.jsonrpc.JsonRpcSender;
import org.asamk.signal.jsonrpc.SignalJsonRpcCommandHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpServerHandler {

    private final static Logger logger = LoggerFactory.getLogger(HttpServerHandler.class);

    private final ObjectMapper objectMapper = Util.createJsonObjectMapper();

    private final InetSocketAddress address;

    private final SignalJsonRpcCommandHandler commandHandler;
    private final MultiAccountManager c;
    private final Manager m;

    public HttpServerHandler(final InetSocketAddress address, final Manager m) {
        this.address = address;
        commandHandler = new SignalJsonRpcCommandHandler(m, Commands::getCommand);
        this.c = null;
        this.m = m;
    }

    public HttpServerHandler(final InetSocketAddress address, final MultiAccountManager c) {
        this.address = address;
        commandHandler = new SignalJsonRpcCommandHandler(c, Commands::getCommand);
        this.c = c;
        this.m = null;
    }

    public void init() throws IOException {
        logger.info("Starting server on " + address.toString());

        final var server = HttpServer.create(address, 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        server.createContext("/api/v1/rpc", this::handleRpcEndpoint);
        server.createContext("/api/v1/events", this::handleEventsEndpoint);
        server.createContext("/api/v1/check", this::handleCheckEndpoint);

        server.start();
    }

    private void sendResponse(int status, Object response, HttpExchange httpExchange) throws IOException {
        if (response != null) {
            final var byteResponse = objectMapper.writeValueAsBytes(response);

            httpExchange.getResponseHeaders().add("Content-Type", "application/json");
            httpExchange.sendResponseHeaders(status, byteResponse.length);

            httpExchange.getResponseBody().write(byteResponse);
        } else {
            httpExchange.sendResponseHeaders(status, -1);
        }

        httpExchange.getResponseBody().close();
    }

    private void handleRpcEndpoint(HttpExchange httpExchange) throws IOException {
        if (!"/api/v1/rpc".equals(httpExchange.getRequestURI().getPath())) {
            sendResponse(404, null, httpExchange);
            return;
        }
        if (!"POST".equals(httpExchange.getRequestMethod())) {
            sendResponse(405, null, httpExchange);
            return;
        }

        final var contentType = httpExchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendResponse(415, null, httpExchange);
            return;
        }

        try {

            final Object[] result = {null};
            final var jsonRpcSender = new JsonRpcSender(s -> {
                if (result[0] != null) {
                    throw new AssertionError("There should only be a single JSON-RPC response");
                }

                result[0] = s;
            });

            final var jsonRpcReader = new JsonRpcReader(jsonRpcSender, httpExchange.getRequestBody());
            jsonRpcReader.readMessages((method, params) -> commandHandler.handleRequest(objectMapper, method, params),
                    response -> logger.debug("Received unexpected response for id {}", response.getId()));

            if (result[0] != null) {
                sendResponse(200, result[0], httpExchange);
            } else {
                sendResponse(201, null, httpExchange);
            }

        } catch (Throwable aEx) {
            logger.error("Failed to process request.", aEx);
            sendResponse(200,
                    JsonRpcResponse.forError(new JsonRpcResponse.Error(JsonRpcResponse.Error.INTERNAL_ERROR,
                            "An internal server error has occurred.",
                            null), null),
                    httpExchange);
        }
    }

    private void handleEventsEndpoint(HttpExchange httpExchange) throws IOException {
        if (!"/api/v1/events".equals(httpExchange.getRequestURI().getPath())) {
            sendResponse(404, null, httpExchange);
            return;
        }
        if (!"GET".equals(httpExchange.getRequestMethod())) {
            sendResponse(405, null, httpExchange);
            return;
        }

        try {
            final var queryString = httpExchange.getRequestURI().getQuery();
            final var query = queryString == null ? Map.<String, String>of() : Util.getQueryMap(queryString);

            List<Manager> managers = getManagerFromQuery(query);
            if (managers == null) {
                sendResponse(400, null, httpExchange);
                return;
            }

            httpExchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            httpExchange.sendResponseHeaders(200, 0);
            final var sender = new ServerSentEventSender(httpExchange.getResponseBody());

            final var shouldStop = new AtomicBoolean(false);
            final var handlers = subscribeReceiveHandlers(managers, sender, () -> {
                shouldStop.set(true);
                synchronized (this) {
                    this.notify();
                }
            });

            try {
                while (true) {
                    synchronized (this) {
                        wait(15_000);
                    }
                    if (shouldStop.get()) {
                        break;
                    }

                    try {
                        sender.sendKeepAlive();
                    } catch (IOException e) {
                        break;
                    }
                }
            } finally {
                for (final var pair : handlers) {
                    unsubscribeReceiveHandler(pair);
                }
                try {
                    httpExchange.getResponseBody().close();
                } catch (IOException ignored) {
                }
            }
        } catch (Throwable aEx) {
            logger.error("Failed to process request.", aEx);
            sendResponse(500, null, httpExchange);
        }
    }

    private void handleCheckEndpoint(HttpExchange httpExchange) throws IOException {
        if (!"/api/v1/check".equals(httpExchange.getRequestURI().getPath())) {
            sendResponse(404, null, httpExchange);
            return;
        }
        if (!"GET".equals(httpExchange.getRequestMethod())) {
            sendResponse(405, null, httpExchange);
            return;
        }

        sendResponse(200, null, httpExchange);
    }

    private List<Manager> getManagerFromQuery(final Map<String, String> query) {
        List<Manager> managers;
        if (m != null) {
            managers = List.of(m);
        } else {
            final var account = query.get("account");
            if (account == null || account.isEmpty()) {
                managers = c.getManagers();
            } else {
                final var manager = c.getManager(account);
                if (manager == null) {
                    return null;
                }
                managers = List.of(manager);
            }
        }
        return managers;
    }

    private List<Pair<Manager, Manager.ReceiveMessageHandler>> subscribeReceiveHandlers(
            final List<Manager> managers, final ServerSentEventSender sender, Callable unsubscribe
    ) {
        return managers.stream().map(m1 -> {
            final var receiveMessageHandler = new JsonReceiveMessageHandler(m1, s -> {
                try {
                    sender.sendEvent(null, "receive", List.of(objectMapper.writeValueAsString(s)));
                } catch (IOException e) {
                    unsubscribe.call();
                }
            });
            m1.addReceiveHandler(receiveMessageHandler);
            return new Pair<>(m1, (Manager.ReceiveMessageHandler) receiveMessageHandler);
        }).toList();
    }

    private void unsubscribeReceiveHandler(final Pair<Manager, Manager.ReceiveMessageHandler> pair) {
        final var m = pair.first();
        final var handler = pair.second();
        m.removeReceiveHandler(handler);
    }

    private interface Callable {

        void call();
    }
}
