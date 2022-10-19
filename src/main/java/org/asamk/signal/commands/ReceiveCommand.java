package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.ReceiveConfig;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public class ReceiveCommand implements LocalCommand {

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
        final var ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));
        final var ignoreStories = Boolean.TRUE.equals(ns.getBoolean("ignore-stories"));
        final var sendReadReceipts = Boolean.TRUE.equals(ns.getBoolean("send-read-receipts"));
        m.setReceiveConfig(new ReceiveConfig(ignoreAttachments, ignoreStories, sendReadReceipts));
        try {
            final var handler = outputWriter instanceof JsonWriter ? new JsonReceiveMessageHandler(m,
                    (JsonWriter) outputWriter) : new ReceiveMessageHandler(m, (PlainTextWriter) outputWriter);
            if (timeout < 0) {
                m.receiveMessages(handler);
            } else {
                m.receiveMessages(Duration.ofMillis((long) (timeout * 1000)), handler);
            }
        } catch (IOException e) {
            throw new IOErrorException("Error while receiving messages: " + e.getMessage(), e);
        }
    }
}
