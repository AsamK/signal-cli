package org.asamk.signal.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.asamk.signal.jsonrpc.JsonRpcResponse;
import org.asamk.signal.jsonrpc.SignalJsonRpcDispatcherHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.concurrent.Executors;

public class HttpServerHandler {

    private final static Logger logger = LoggerFactory.getLogger(HttpServerHandler.class);

    private final ObjectMapper objectMapper = Util.createJsonObjectMapper();

    private final int port;

    private final Manager m;

    private final MultiAccountManager c;

    public HttpServerHandler(final int port, final Manager m) {
        this.port = port;
        this.m = m;
        this.c = null;
    }

    public HttpServerHandler(final int port, final MultiAccountManager c) {
        this.port = port;
        this.m = null;
        this.c = c;
    }

    public void init() {

        try {

            logger.info("Starting server on port " + port);

            final var server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(10));

            server.createContext("/json-rpc", httpExchange -> {

                try {
                    if (!"POST".equals(httpExchange.getRequestMethod())) {
                        throw new HttpServerException(405, "Method not supported.");
                    }

                    // Create a custom writer which receives our response
                    final var valueHolder = new Object[] { null };
                    final JsonWriter jsonWriter = (Object o) -> valueHolder[0] = o;

                    // This queue is used to deliver our request and then deliver a null
                    // value which terminates the reading process
                    final var stringRequest = IOUtils.readAll(httpExchange.getRequestBody(), StandardCharsets.UTF_8);
                    final var queue = new LinkedList<String>();
                    queue.addLast(stringRequest);
                    queue.addLast(null);

                    // Create dispatcher and handle connection
                    // Right now we are creating a new one for every request. This may not be
                    // totally efficient, but it handles possible issues that would arise with
                    // multithreading.
                    final var dispatcher = new SignalJsonRpcDispatcherHandler(
                            jsonWriter,
                            queue::removeFirst,
                            true
                    );

                    if (c != null) {
                        dispatcher.handleConnection(c);
                    } else {
                        dispatcher.handleConnection(m);
                    }

                    // Extract and process the response
                    final var response = valueHolder[0] != null ? valueHolder[0] : JsonRpcResponse.forSuccess(null, null);

                    if (response instanceof JsonRpcResponse jsonRpcResponse) {
                        if (jsonRpcResponse.getError() == null) {
                            sendResponse(200, jsonRpcResponse, httpExchange);
                        } else {
                            sendResponse(500, jsonRpcResponse, httpExchange);
                        }
                    } else {
                        logger.error("Invalid response object was received." + response);
                        throw new HttpServerException(500, "An internal server error has occurred.");
                    }
                }
                catch (HttpServerException aEx) {
                    logger.error("Failed to process request.", aEx);
                    sendResponse(aEx.getHttpStatus(), JsonRpcResponse.forError(
                            new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_REQUEST,
                                    aEx.getMessage(), null), null), httpExchange);
                }
                catch (Throwable aEx) {
                    logger.error("Failed to process request.", aEx);
                    sendResponse(500, JsonRpcResponse.forError(
                            new JsonRpcResponse.Error(JsonRpcResponse.Error.INTERNAL_ERROR,
                            "An internal server error has occurred.", null), null), httpExchange);
                }
            });

            server.start();

        } catch (Throwable ex) {
            ex.printStackTrace();
        }

    }

    private void sendResponse(int status, JsonRpcResponse response, HttpExchange httpExchange) throws IOException {
        final var byteResponse = objectMapper.writeValueAsBytes(response);
        httpExchange.sendResponseHeaders(status, byteResponse.length);
        httpExchange.getResponseBody().write(byteResponse);
        httpExchange.getResponseBody().close();
    }

}
