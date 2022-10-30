package org.asamk.signal.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.JsonRpcNamespace;
import org.asamk.signal.commands.LocalCommand;
import org.asamk.signal.commands.RegistrationCommand;
import org.asamk.signal.jsonrpc.JsonRpcException;
import org.asamk.signal.jsonrpc.JsonRpcRequest;
import org.asamk.signal.jsonrpc.JsonRpcResponse;
import org.asamk.signal.jsonrpc.SignalJsonRpcDispatcherHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.output.JsonWriterImpl;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public class HttpServerHandler {

    private final static Logger logger = LoggerFactory.getLogger(SignalJsonRpcDispatcherHandler.class);

    private final ObjectMapper objectMapper = Util.createJsonObjectMapper();

    public void init(int port, MultiAccountManager m) {

        try {

            System.out.println("Starting server on port " + port);

            final var server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(10));

            server.createContext("/api/v1", httpExchange -> {
                try {
                    if (!"POST".equals(httpExchange.getRequestMethod())) {
                        sendResponse(405, "NOT SUPPORTED", httpExchange);
                    }

                    final var request = objectMapper.readValue(httpExchange.getRequestBody(), JsonRpcRequest.class);
                    final Map params = objectMapper.treeToValue(request.getParams(), Map.class);

                    System.out.println("Command called " + request.getMethod());

                    final var command = Commands.getCommand(request.getMethod());
                    final var writer = new StringWriter();
                    final var ns = new JsonRpcNamespace(params);

                    if (command instanceof LocalCommand) {
                        final var manager = getManagerFromParams(request.getParams(), m);
                        ((LocalCommand) command).handleCommand(ns, manager, new JsonWriterImpl(writer));
                    } else if (command instanceof RegistrationCommand) {
                        final var registrationManager = getRegistrationManagerFromParams(request.getParams(), m);
                        if (registrationManager != null) {
                            ((RegistrationCommand) command).handleCommand(ns, registrationManager);
                        } else {
                            throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_PARAMS,
                                    "Method requires valid account parameter",
                                    null));
                        }
                    }
                    else {
                        sendResponse(404, "COMMAND NOT FOUND", httpExchange);
                        return;
                    }

                    // TODO if writer empty return some generic response
                    sendResponse(200, writer.toString(), httpExchange);

                }
                catch (Throwable aEx) {
                    aEx.printStackTrace();
                    //TODO if this is a JSON RPC Error serialize and return the error
                    sendResponse(500, "ERROR", httpExchange);
                }
            });

            server.start();

            // TODO there may be a better way to keep the main thread running.
            try {
                while (true) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ex) { }

            System.out.println("Server shut down");

        } catch (Throwable ex) {
            ex.printStackTrace();
        }

    }

    private void sendResponse(int status, String body, HttpExchange httpExchange) throws IOException {
        final var byteResponse = body.getBytes(StandardCharsets.UTF_8);
        httpExchange.sendResponseHeaders(status, byteResponse.length);
        httpExchange.getResponseBody().write(byteResponse);
        httpExchange.getResponseBody().close();
    }

    private Manager getManagerFromParams(final ContainerNode<?> params, MultiAccountManager c) throws JsonRpcException {
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

    private RegistrationManager getRegistrationManagerFromParams(final ContainerNode<?> params, MultiAccountManager c) {
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

}
