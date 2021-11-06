package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputType;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        double timeout = ns.getDouble("timeout");
        boolean ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));
        m.setIgnoreAttachments(ignoreAttachments);
        try {
            final var handler = outputWriter instanceof JsonWriter ? new JsonReceiveMessageHandler(m,
                    (JsonWriter) outputWriter) : new ReceiveMessageHandler(m, (PlainTextWriter) outputWriter);
            if (timeout < 0) {
                m.receiveMessages(handler);
            } else {
                m.receiveMessages((long) (timeout * 1000), TimeUnit.MILLISECONDS, handler);
            }
        } catch (IOException e) {
            throw new IOErrorException("Error while receiving messages: " + e.getMessage(), e);
        }
    }
}
