package org.asamk.signal.jsonrpc;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.JsonRpcMultiCommand;
import org.asamk.signal.commands.JsonRpcRegistrationCommand;
import org.asamk.signal.commands.JsonRpcSingleCommand;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.RateLimitErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.output.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.util.Map;
import java.util.function.Function;

public class SignalJsonRpcCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(SignalJsonRpcDispatcherHandler.class);

    private static final int USER_ERROR = -1;
    private static final int IO_ERROR = -3;
    private static final int UNTRUSTED_KEY_ERROR = -4;
    private static final int RATELIMIT_ERROR = -5;

    private final Manager m;
    private final MultiAccountManager c;
    private final Function<String, Command> commandProvider;

    public SignalJsonRpcCommandHandler(final Manager m, final Function<String, Command> commandProvider) {
        this.c = null;
        this.m = m;
        this.commandProvider = commandProvider;
    }

    public SignalJsonRpcCommandHandler(final MultiAccountManager c, final Function<String, Command> commandProvider) {
        this.c = c;
        this.m = null;
        this.commandProvider = commandProvider;
    }

    public JsonNode handleRequest(
            final ObjectMapper objectMapper,
            final String method,
            ContainerNode<?> params
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

            var manager = getManagerFromParams(params);
            if (manager == null) {
                final var managers = c.getManagers();
                if (managers.size() == 1) {
                    manager = managers.getFirst();
                }
            }
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
            final var number = params.get("account").asText();
            try {
                final var registrationManager = c.getNewRegistrationManager(number);
                ((ObjectNode) params).remove("account");
                return registrationManager;
            } catch (OverlappingFileLockException e) {
                logger.warn("Account is already in use, attempting to close existing manager and retry: {}", number);
                try {
                    final var existingManager = c.getManager(number);
                    if (existingManager != null) {
                        existingManager.close();
                    }
                } catch (Throwable closeError) {
                    logger.warn("Failed to close existing manager for {}: {}", number, closeError.getMessage());
                }
                try {
                    final var registrationManager = c.getNewRegistrationManager(number);
                    ((ObjectNode) params).remove("account");
                    return registrationManager;
                } catch (OverlappingFileLockException e2) {
                    logger.warn("Account still in use after closing manager: {}", number);
                    return null;
                } catch (IOException | IllegalStateException e2) {
                    logger.warn("Failed to load registration manager after retry", e2);
                    return null;
                }
            } catch (IOException | IllegalStateException e) {
                logger.warn("Failed to load registration manager", e);
                return null;
            }
        }
        return null;
    }

    private Command getCommand(final String method) {
        return commandProvider.apply(method);
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
            final ObjectMapper objectMapper,
            final ContainerNode<?> params,
            final CommandRunner<?> command
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
        } catch (CommandException ce) {
            switch (ce) {
                case UserErrorException e -> throw new JsonRpcException(new JsonRpcResponse.Error(USER_ERROR,
                        e.getMessage(),
                        getErrorDataNode(objectMapper, result)));
                case IOErrorException e -> throw new JsonRpcException(new JsonRpcResponse.Error(IO_ERROR,
                        e.getMessage(),
                        getErrorDataNode(objectMapper, result)));
                case UntrustedKeyErrorException e -> throw new JsonRpcException(new JsonRpcResponse.Error(
                        UNTRUSTED_KEY_ERROR,
                        e.getMessage(),
                        getErrorDataNode(objectMapper, result)));
                case RateLimitErrorException e -> throw new JsonRpcException(new JsonRpcResponse.Error(RATELIMIT_ERROR,
                        e.getMessage(),
                        getErrorDataNode(objectMapper, result)));
                case UnexpectedErrorException e -> {
                    logger.error("Command execution failed with unexpected error", e);
                    throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INTERNAL_ERROR,
                            e.getMessage() + " (" + e.getClass().getSimpleName() + ")",
                            getErrorDataNode(objectMapper, result)));
                }
            }
        } catch (Throwable e) {
            logger.error("Command execution failed", e);
            throw new JsonRpcException(new JsonRpcResponse.Error(JsonRpcResponse.Error.INTERNAL_ERROR,
                    e.getMessage() + " (" + e.getClass().getSimpleName() + ")",
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
}
