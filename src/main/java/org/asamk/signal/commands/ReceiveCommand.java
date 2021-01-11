package org.asamk.signal.commands;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.json.JsonMessageEnvelope;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.DateUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;

public class ReceiveCommand implements ExtendedDbusCommand, LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-t", "--timeout")
                .type(double.class)
                .help("Number of seconds to wait for new messages (negative values disable timeout)");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--json")
                .help("WARNING: This parameter is now deprecated! Please use the \"output\" option instead.\n\nOutput received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    public int handleCommand(final Namespace ns, final Signal signal, DBusConnection dbusconnection) {
        final ObjectMapper jsonProcessor;

        boolean inJson = ns.getString("output").equals("json") || ns.getBoolean("json");

        if (inJson) {
            jsonProcessor = new ObjectMapper();
            jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
            jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        } else {
            jsonProcessor = null;
        }
        try {
            dbusconnection.addSigHandler(Signal.MessageReceived.class, messageReceived -> {
                if (jsonProcessor != null) {
                    JsonMessageEnvelope envelope = new JsonMessageEnvelope(messageReceived);
                    ObjectNode result = jsonProcessor.createObjectNode();
                    result.putPOJO("envelope", envelope);
                    try {
                        jsonProcessor.writeValue(System.out, result);
                        System.out.println();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.print(String.format("Envelope from: %s\nTimestamp: %s\nBody: %s\n",
                            messageReceived.getSender(),
                            DateUtils.formatTimestamp(messageReceived.getTimestamp()),
                            messageReceived.getMessage()));
                    if (messageReceived.getGroupId().length > 0) {
                        System.out.println("Group info:");
                        System.out.println("  Id: " + Base64.encodeBytes(messageReceived.getGroupId()));
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
                if (jsonProcessor != null) {
                    JsonMessageEnvelope envelope = new JsonMessageEnvelope(receiptReceived);
                    ObjectNode result = jsonProcessor.createObjectNode();
                    result.putPOJO("envelope", envelope);
                    try {
                        jsonProcessor.writeValue(System.out, result);
                        System.out.println();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.print(String.format("Receipt from: %s\nTimestamp: %s\n",
                            receiptReceived.getSender(),
                            DateUtils.formatTimestamp(receiptReceived.getTimestamp())));
                }
            });

            dbusconnection.addSigHandler(Signal.SyncMessageReceived.class, syncReceived -> {
                if (jsonProcessor != null) {
                    JsonMessageEnvelope envelope = new JsonMessageEnvelope(syncReceived);
                    ObjectNode result = jsonProcessor.createObjectNode();
                    result.putPOJO("envelope", envelope);
                    try {
                        jsonProcessor.writeValue(System.out, result);
                        System.out.println();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.print(String.format("Sync Envelope from: %s to: %s\nTimestamp: %s\nBody: %s\n",
                            syncReceived.getSource(),
                            syncReceived.getDestination(),
                            DateUtils.formatTimestamp(syncReceived.getTimestamp()),
                            syncReceived.getMessage()));
                    if (syncReceived.getGroupId().length > 0) {
                        System.out.println("Group info:");
                        System.out.println("  Id: " + Base64.encodeBytes(syncReceived.getGroupId()));
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
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Missing native library dependency for dbus service: " + e.getMessage());
            return 1;
        } catch (DBusException e) {
            e.printStackTrace();
            return 1;
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
        boolean inJson = ns.getString("output").equals("json") || ns.getBoolean("json");

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
