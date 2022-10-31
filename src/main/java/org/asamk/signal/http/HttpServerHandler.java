package org.asamk.signal.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.JsonRpcNamespace;
import org.asamk.signal.commands.LocalCommand;
import org.asamk.signal.commands.MultiLocalCommand;
import org.asamk.signal.commands.RegistrationCommand;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.jsonrpc.JsonRpcException;
import org.asamk.signal.jsonrpc.JsonRpcRequest;
import org.asamk.signal.jsonrpc.JsonRpcResponse;
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
import java.util.Map;
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

            server.createContext("/api/v1", httpExchange -> {

                JsonRpcRequest request = null;

                try {
                    if (!"POST".equals(httpExchange.getRequestMethod())) {
                        throw new HttpServerException(405, "Method not supported.");
                    }

                    request = objectMapper.readValue(httpExchange.getRequestBody(), JsonRpcRequest.class);
                    final Map params = objectMapper.treeToValue(request.getParams(), Map.class);

                    logger.debug("Command called " + request.getMethod());

                    final var ns = new JsonRpcNamespace(params);

                    final var responseBody = processRequest(ns, request);

                    sendResponse(200, responseBody, httpExchange);

                }
                catch (JsonRpcException aEx) {
                    logger.error("Failed to process request.", aEx);
                    sendResponse(500, JsonRpcResponse.forError(aEx.getError(), request.getId()), httpExchange);
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
                            "An internal server error has occured.", null), null), httpExchange);
                }
            });

            server.start();

        } catch (Throwable ex) {
            ex.printStackTrace();
        }

    }

    private JsonRpcResponse processRequest(
            final JsonRpcNamespace ns,
            final JsonRpcRequest request
    ) throws JsonRpcException, CommandException, IOException {

        final var writer = new StringWriter();
        final var command = Commands.getCommand(request.getMethod());

        if (command instanceof LocalCommand) {
            final Manager manager;
            if (c != null) {
                manager = getManagerFromParams(request.getParams(), c);
            } else {
                manager = m;
            }
            ((LocalCommand) command).handleCommand(ns, manager, new JsonWriterImpl(writer));
        } else if (command instanceof MultiLocalCommand) {
            if (c == null) {
                throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_PARAMS,
                        "Cannot run multi command when running in single mode.",
                        null));
            }
            ((MultiLocalCommand) command).handleCommand(ns, c, new JsonWriterImpl(writer));
        } else if (command instanceof RegistrationCommand) {
            if (c == null) {
                throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_PARAMS,
                        "Cannot run multi command when running in single mode.",
                        null));
            }
            final var registrationManager = getRegistrationManagerFromParams(request.getParams(), c);
            if (registrationManager != null) {
                ((RegistrationCommand) command).handleCommand(ns, registrationManager);
            } else {
                throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INVALID_PARAMS,
                        "Method requires valid account parameter",
                        null));
            }
        }
        else {
            throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.METHOD_NOT_FOUND,
                    "The specified method is not supported.",
                    null));
        }

        final var rawJson = writer.toString();
        final JsonNode dataNode;

        if (rawJson.isEmpty()) {
            dataNode = new POJONode(new HttpSimpleResponse("OK"));
        } else {
            dataNode = objectMapper.readTree(rawJson);
        }

        return JsonRpcResponse.forSuccess(dataNode, request.getId());
    }

    private void sendResponse(int status, JsonRpcResponse response, HttpExchange httpExchange) throws IOException {
        final var byteResponse = objectMapper.writeValueAsBytes(response);
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
