package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.AlreadyReceivingException;
import org.asamk.signal.manager.api.ReceiveConfig;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReceiveCommand implements LocalCommand, JsonRpcSingleCommand<ReceiveCommand.ReceiveParams> {

    private final static Logger logger = LoggerFactory.getLogger(ReceiveCommand.class);

    @Override
    public String getName() {
        return "receive";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Query the server for new messages.");
        subparser.addArgument("-t", "--timeout")
                .type(double.class)
                .setDefault(3.0)
                .help("Number of seconds to wait for new messages (negative values disable timeout)");
        subparser.addArgument("--max-messages")
                .type(int.class)
                .setDefault(-1)
                .help("Maximum number of messages to receive, before returning.");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--ignore-stories")
                .help("Don’t receive story messages from the server.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--send-read-receipts")
                .help("Send read receipts for all incoming data messages (in addition to the default delivery receipts)")
                .action(Arguments.storeTrue());
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var timeout = ns.getDouble("timeout");
        final var maxMessagesRaw = ns.getInt("max-messages");
        final var ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));
        final var ignoreStories = Boolean.TRUE.equals(ns.getBoolean("ignore-stories"));
        final var sendReadReceipts = Boolean.TRUE.equals(ns.getBoolean("send-read-receipts"));
        m.setReceiveConfig(new ReceiveConfig(ignoreAttachments, ignoreStories, sendReadReceipts));
        try {
            final var handler = outputWriter instanceof JsonWriter ? new JsonReceiveMessageHandler(m,
                    (JsonWriter) outputWriter) : new ReceiveMessageHandler(m, (PlainTextWriter) outputWriter);
            final var duration = timeout < 0 ? null : Duration.ofMillis((long) (timeout * 1000));
            final var maxMessages = maxMessagesRaw < 0 ? null : maxMessagesRaw;
            m.receiveMessages(Optional.ofNullable(duration), Optional.ofNullable(maxMessages), handler);
        } catch (IOException e) {
            throw new IOErrorException("Error while receiving messages: " + e.getMessage(), e);
        } catch (AlreadyReceivingException e) {
            throw new UserErrorException("Receive command cannot be used if messages are already being received.", e);
        }
    }

    @Override
    public TypeReference<ReceiveParams> getRequestType() {
        return new TypeReference<>() {};
    }

    @Override
    public void handleCommand(
            final ReceiveParams request, final Manager m, final JsonWriter jsonWriter
    ) throws CommandException {
        final var timeout = request.timeout() == null ? 3.0 : request.timeout();
        final var maxMessagesRaw = request.maxMessages() == null ? -1 : request.maxMessages();

        try {
            final var messages = new ArrayList<>();
            final var handler = new JsonReceiveMessageHandler(m, messages::add);
            final var duration = timeout < 0 ? null : Duration.ofMillis((long) (timeout * 1000));
            final var maxMessages = maxMessagesRaw < 0 ? null : maxMessagesRaw;
            m.receiveMessages(Optional.ofNullable(duration), Optional.ofNullable(maxMessages), handler);
            jsonWriter.write(messages);
        } catch (IOException e) {
            throw new IOErrorException("Error while receiving messages: " + e.getMessage(), e);
        } catch (AlreadyReceivingException e) {
            throw new UserErrorException("Receive command cannot be used if messages are already being received.", e);
        }
    }

    public record ReceiveParams(Double timeout, Integer maxMessages) {}
}
