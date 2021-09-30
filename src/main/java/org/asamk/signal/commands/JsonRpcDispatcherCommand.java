package org.asamk.signal.commands;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ContainerNode;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputType;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.jsonrpc.JsonRpcException;
import org.asamk.signal.jsonrpc.JsonRpcReader;
import org.asamk.signal.jsonrpc.JsonRpcRequest;
import org.asamk.signal.jsonrpc.JsonRpcResponse;
import org.asamk.signal.jsonrpc.JsonRpcSender;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class JsonRpcDispatcherCommand implements LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(JsonRpcDispatcherCommand.class);

    private static final int USER_ERROR = -1;
    private static final int IO_ERROR = -3;
    private static final int UNTRUSTED_KEY_ERROR = -4;

    @Override
    public String getName() {
        return "jsonRpc";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Take commands from standard input as line-delimited JSON RPC while receiving messages.");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final boolean ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));

        final var objectMapper = Util.createJsonObjectMapper();
        final var jsonRpcSender = new JsonRpcSender((JsonWriter) outputWriter);

        final var receiveThread = receiveMessages(s -> jsonRpcSender.sendRequest(JsonRpcRequest.forNotification(
                "receive",
                objectMapper.valueToTree(s),
                null)), m, ignoreAttachments);

        // Maybe this should be handled inside the Manager
        while (!m.hasCaughtUpWithOldMessages()) {
            try {
                synchronized (m) {
                    m.wait();
                }
            } catch (InterruptedException ignored) {
            }
        }

        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        final var jsonRpcReader = new JsonRpcReader(jsonRpcSender, () -> {
            try {
                return reader.readLine();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });
        jsonRpcReader.readRequests((method, params) -> handleRequest(m, objectMapper, method, params),
                response -> logger.debug("Received unexpected response for id {}", response.getId()));

        receiveThread.interrupt();
        try {
            receiveThread.join();
        } catch (InterruptedException ignored) {
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

    private Thread receiveMessages(
            JsonWriter jsonWriter, Manager m, boolean ignoreAttachments
    ) {
        final var thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    final var receiveMessageHandler = new JsonReceiveMessageHandler(m, jsonWriter);
                    m.receiveMessages(1, TimeUnit.HOURS, false, ignoreAttachments, receiveMessageHandler);
                    break;
                } catch (IOException e) {
                    logger.warn("Receiving messages failed, retrying", e);
                }
            }
        });

        thread.start();

        return thread;
    }
}
