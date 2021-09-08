package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputType;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.json.JsonMessageEnvelope;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.DateUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ReceiveCommand implements ExtendedDbusCommand, LocalCommand {

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

    public void handleCommand(
            final Namespace ns, final Signal signal, DBusConnection dbusconnection, final OutputWriter outputWriter
    ) throws CommandException {
        try {
            if (outputWriter instanceof JsonWriter) {
                final var jsonWriter = (JsonWriter) outputWriter;

                dbusconnection.addSigHandler(Signal.MessageReceived.class, signal, messageReceived -> {
                    var envelope = new JsonMessageEnvelope(messageReceived);
                    final var object = Map.of("envelope", envelope);
                    jsonWriter.write(object);
                });

                dbusconnection.addSigHandler(Signal.ReceiptReceived.class, signal, receiptReceived -> {
                    var envelope = new JsonMessageEnvelope(receiptReceived);
                    final var object = Map.of("envelope", envelope);
                    jsonWriter.write(object);
                });

                dbusconnection.addSigHandler(Signal.SyncMessageReceived.class, signal, syncReceived -> {
                    var envelope = new JsonMessageEnvelope(syncReceived);
                    final var object = Map.of("envelope", envelope);
                    jsonWriter.write(object);
                });
            } else {
                final var writer = (PlainTextWriter) outputWriter;

                dbusconnection.addSigHandler(Signal.MessageReceived.class, signal, messageReceived -> {
                    writer.println("Envelope from: {}", messageReceived.getSender());
                    writer.println("Timestamp: {}", DateUtils.formatTimestamp(messageReceived.getTimestamp()));
                    writer.println("Body: {}", messageReceived.getMessage());
                    if (messageReceived.getGroupId().length > 0) {
                        writer.println("Group info:");
                        writer.indentedWriter()
                                .println("Id: {}", Base64.getEncoder().encodeToString(messageReceived.getGroupId()));
                    }
                    if (messageReceived.getAttachments().size() > 0) {
                        writer.println("Attachments:");
                        for (var attachment : messageReceived.getAttachments()) {
                            writer.println("- Stored plaintext in: {}", attachment);
                        }
                    }
                    writer.println();
                });

                dbusconnection.addSigHandler(Signal.ReceiptReceived.class, signal, receiptReceived -> {
                    writer.println("Receipt from: {}", receiptReceived.getSender());
                    writer.println("Timestamp: {}", DateUtils.formatTimestamp(receiptReceived.getTimestamp()));
                });

                dbusconnection.addSigHandler(Signal.SyncMessageReceived.class, signal, syncReceived -> {
                    writer.println("Sync Envelope from: {} to: {}",
                            syncReceived.getSource(),
                            syncReceived.getDestination());
                    writer.println("Timestamp: {}", DateUtils.formatTimestamp(syncReceived.getTimestamp()));
                    writer.println("Body: {}", syncReceived.getMessage());
                    if (syncReceived.getGroupId().length > 0) {
                        writer.println("Group info:");
                        writer.indentedWriter()
                                .println("Id: {}", Base64.getEncoder().encodeToString(syncReceived.getGroupId()));
                    }
                    if (syncReceived.getAttachments().size() > 0) {
                        writer.println("Attachments:");
                        for (var attachment : syncReceived.getAttachments()) {
                            writer.println("- Stored plaintext in: {}", attachment);
                        }
                    }
                    writer.println();
                });
            }
        } catch (DBusException e) {
            logger.error("Dbus client failed", e);
            throw new UnexpectedErrorException("Dbus client failed", e);
        }
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
                return;
            }
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        double timeout = ns.getDouble("timeout");
        var returnOnTimeout = true;
        if (timeout < 0) {
            returnOnTimeout = false;
            timeout = 3600;
        }
        boolean ignoreAttachments = ns.getBoolean("ignore-attachments");
        try {
            final var handler = outputWriter instanceof JsonWriter ? new JsonReceiveMessageHandler(m,
                    (JsonWriter) outputWriter) : new ReceiveMessageHandler(m, (PlainTextWriter) outputWriter);
            m.receiveMessages((long) (timeout * 1000),
                    TimeUnit.MILLISECONDS,
                    returnOnTimeout,
                    ignoreAttachments,
                    handler);
        } catch (IOException e) {
            throw new IOErrorException("Error while receiving messages: " + e.getMessage(), e);
        }
    }
}
