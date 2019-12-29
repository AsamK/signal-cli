package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.DateUtils;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusSigHandler;
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
                .help("Output received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    public int handleCommand(final Namespace ns, final Signal signal, DBusConnection dbusconnection) {
        if (dbusconnection != null) {
            try {
                dbusconnection.addSigHandler(Signal.MessageReceived.class, new DBusSigHandler<Signal.MessageReceived>() {
                    @Override
                    public void handle(Signal.MessageReceived s) {
                        System.out.print(String.format("Envelope from: %s\nTimestamp: %s\nBody: %s\n",
                                s.getSender(), DateUtils.formatTimestamp(s.getTimestamp()), s.getMessage()));
                        if (s.getGroupId().length > 0) {
                            System.out.println("Group info:");
                            System.out.println("  Id: " + Base64.encodeBytes(s.getGroupId()));
                        }
                        if (s.getAttachments().size() > 0) {
                            System.out.println("Attachments: ");
                            for (String attachment : s.getAttachments()) {
                                System.out.println("-  Stored plaintext in: " + attachment);
                            }
                        }
                        System.out.println();
                    }
                });
                dbusconnection.addSigHandler(Signal.ReceiptReceived.class, new DBusSigHandler<Signal.ReceiptReceived>() {
                    @Override
                    public void handle(Signal.ReceiptReceived s) {
                        System.out.print(String.format("Receipt from: %s\nTimestamp: %s\n",
                                s.getSender(), DateUtils.formatTimestamp(s.getTimestamp())));
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
        return 0;
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
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
            final Manager.ReceiveMessageHandler handler = ns.getBoolean("json") ? new JsonReceiveMessageHandler(m) : new ReceiveMessageHandler(m);
            m.receiveMessages((long) (timeout * 1000), TimeUnit.MILLISECONDS, returnOnTimeout, ignoreAttachments, handler);
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
