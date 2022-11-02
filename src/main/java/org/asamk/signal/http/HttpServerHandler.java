package org.asamk.signal.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.asamk.signal.commands.Commands;
import org.asamk.signal.jsonrpc.JsonRpcReader;
import org.asamk.signal.jsonrpc.JsonRpcResponse;
import org.asamk.signal.jsonrpc.JsonRpcSender;
import org.asamk.signal.jsonrpc.SignalJsonRpcCommandHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class HttpServerHandler {

    private final static Logger logger = LoggerFactory.getLogger(HttpServerHandler.class);

    private final ObjectMapper objectMapper = Util.createJsonObjectMapper();

    private final InetSocketAddress address;

    private final SignalJsonRpcCommandHandler commandHandler;

    public HttpServerHandler(final InetSocketAddress address, final Manager m) {
        this.address = address;
        commandHandler = new SignalJsonRpcCommandHandler(m, Commands::getCommand);
    }

    public HttpServerHandler(final InetSocketAddress address, final MultiAccountManager c) {
        this.address = address;
        commandHandler = new SignalJsonRpcCommandHandler(c, Commands::getCommand);
    }

    public void init() throws IOException {

            logger.info("Starting server on " + address.toString());

            final var server = HttpServer.create(address, 0);
            server.setExecutor(Executors.newFixedThreadPool(10));

            server.createContext("/api/v1/rpc", httpExchange -> {

                if (!"POST".equals(httpExchange.getRequestMethod())) {
                    sendResponse(405, null, httpExchange);
                    return;
                }

                if (!"application/json".equals(httpExchange.getRequestHeaders().getFirst("Content-Type"))) {
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

                    if (result[0] !=null) {
                        sendResponse(200, result[0], httpExchange);
                    } else {
                        sendResponse(201, null, httpExchange);
                    }

                }
                catch (Throwable aEx) {
                    logger.error("Failed to process request.", aEx);
                    sendResponse(200, JsonRpcResponse.forError(
                            new JsonRpcResponse.Error(JsonRpcResponse.Error.INTERNAL_ERROR,
                            "An internal server error has occurred.", null), null), httpExchange);
                }
            });

            server.start();

    }

    private void sendResponse(int status, Object response, HttpExchange httpExchange) throws IOException {
        if (response != null) {
            final var byteResponse = objectMapper.writeValueAsBytes(response);

            httpExchange.getResponseHeaders().add("Content-Type", "application/json");
            httpExchange.sendResponseHeaders(status, byteResponse.length);

            httpExchange.getResponseBody().write(byteResponse);
        } else {
            httpExchange.sendResponseHeaders(status, 0);
        }

        httpExchange.getResponseBody().close();
    }

}
