package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputType;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.json.JsonMessageEnvelope;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.DateUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;

public class ReceiveCommand implements ExtendedDbusCommand, LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(ReceiveCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-t", "--timeout")
                .type(double.class)
                .help("Number of seconds to wait for new messages (negative values disable timeout)");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--json")
                .help("WARNING: This parameter is now deprecated! Please use the global \"--output=json\" option instead.\n\nOutput received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    @Override
    public Set<OutputType> getSupportedOutputTypes() {
        return Set.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    public int handleCommand(final Namespace ns, final Signal signal, DBusConnection dbusconnection) {
        boolean inJson = ns.get("output") == OutputType.JSON || ns.getBoolean("json");

        // TODO delete later when "json" variable is removed
        if (ns.getBoolean("json")) {
            logger.warn("\"--json\" option has been deprecated, please use the global \"--output=json\" instead.");
        }

        final JsonWriter jsonWriter = inJson ? new JsonWriter(System.out) : null;
        try {
            dbusconnection.addSigHandler(Signal.MessageReceived.class, messageReceived -> {
                if (jsonWriter != null) {
                    JsonMessageEnvelope envelope = new JsonMessageEnvelope(messageReceived);
                    final Map<String, JsonMessageEnvelope> object = Map.of("envelope", envelope);
                    try {
                        jsonWriter.write(object);
                    } catch (IOException e) {
                        logger.error("Failed to write json object: {}", e.getMessage());
                    }
                } else {
                    System.out.print(String.format("Envelope from: %s\nTimestamp: %s\nBody: %s\n",
                            messageReceived.getSender(),
                            DateUtils.formatTimestamp(messageReceived.getTimestamp()),
                            messageReceived.getMessage()));
                    if (messageReceived.getGroupId().length > 0) {
                        System.out.println("Group info:");
                        System.out.println("  Id: " + Base64.getEncoder().encodeToString(messageReceived.getGroupId()));
                    }
                    if (messageReceived.getAttachments().size() > 0) {
                        System.out.println("Attachments: ");
                        for (String attachment : messageReceived.getAttachments()) {
                            System.out.println("-  Stored plaintext in: " + attachment);
                        }
                    }
                    System.out.println();
                }
            });

            dbusconnection.addSigHandler(Signal.ReceiptReceived.class, receiptReceived -> {
                if (jsonWriter != null) {
                    JsonMessageEnvelope envelope = new JsonMessageEnvelope(receiptReceived);
                    final Map<String, JsonMessageEnvelope> object = Map.of("envelope", envelope);
                    try {
                        jsonWriter.write(object);
                    } catch (IOException e) {
                        logger.error("Failed to write json object: {}", e.getMessage());
                    }
                } else {
                    System.out.print(String.format("Receipt from: %s\nTimestamp: %s\n",
                            receiptReceived.getSender(),
                            DateUtils.formatTimestamp(receiptReceived.getTimestamp())));
                }
            });

            dbusconnection.addSigHandler(Signal.SyncMessageReceived.class, syncReceived -> {
                if (jsonWriter != null) {
                    JsonMessageEnvelope envelope = new JsonMessageEnvelope(syncReceived);
                    final Map<String, JsonMessageEnvelope> object = Map.of("envelope", envelope);
                    try {
                        jsonWriter.write(object);
                    } catch (IOException e) {
                        logger.error("Failed to write json object: {}", e.getMessage());
                    }
                } else {
                    System.out.print(String.format("Sync Envelope from: %s to: %s\nTimestamp: %s\nBody: %s\n",
                            syncReceived.getSource(),
                            syncReceived.getDestination(),
                            DateUtils.formatTimestamp(syncReceived.getTimestamp()),
                            syncReceived.getMessage()));
                    if (syncReceived.getGroupId().length > 0) {
                        System.out.println("Group info:");
                        System.out.println("  Id: " + Base64.getEncoder().encodeToString(syncReceived.getGroupId()));
                    }
                    if (syncReceived.getAttachments().size() > 0) {
                        System.out.println("Attachments: ");
                        for (String attachment : syncReceived.getAttachments()) {
                            System.out.println("-  Stored plaintext in: " + attachment);
                        }
                    }
                    System.out.println();
                }
            });
        } catch (DBusException e) {
            e.printStackTrace();
            return 2;
        }
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                return 0;
            }
        }
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        boolean inJson = ns.get("output") == OutputType.JSON || ns.getBoolean("json");

        // TODO delete later when "json" variable is removed
        if (ns.getBoolean("json")) {
            logger.warn("\"--json\" option has been deprecated, please use the global \"--output=json\" instead.");
        }

        double timeout = 5;
        if (ns.getDouble("timeout") != null) {
            timeout = ns.getDouble("timeout");
        }
        boolean returnOnTimeout = true;
        if (timeout < 0) {
            returnOnTimeout = false;
            timeout = 3600;
        }
        boolean ignoreAttachments = ns.getBoolean("ignore_attachments");
        try {
            final Manager.ReceiveMessageHandler handler = inJson
                    ? new JsonReceiveMessageHandler(m)
                    : new ReceiveMessageHandler(m);
            m.receiveMessages((long) (timeout * 1000),
                    TimeUnit.MILLISECONDS,
                    returnOnTimeout,
                    ignoreAttachments,
                    handler);
            return 0;
        } catch (IOException e) {
            System.err.println("Error while receiving messages: " + e.getMessage());
            return 3;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        }
    }
}
