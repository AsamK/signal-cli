/**
 * Copyright (C) 2015 AsamK
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.asamk.signal;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.util.TextUtils;
import org.asamk.Signal;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class Main {

    public static final String SIGNAL_BUSNAME = "org.asamk.Signal";
    public static final String SIGNAL_OBJECTPATH = "/org/asamk/Signal";

    public static void main(String[] args) {
        // Workaround for BKS truststore
        Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1);

        Namespace ns = parseArgs(args);
        if (ns == null) {
            System.exit(1);
        }

        final String username = ns.getString("username");
        Manager m;
        Signal ts;
        DBusConnection dBusConn = null;
        try {
            if (ns.getBoolean("dbus") || ns.getBoolean("dbus_system")) {
                try {
                    m = null;
                    int busType;
                    if (ns.getBoolean("dbus_system")) {
                        busType = DBusConnection.SYSTEM;
                    } else {
                        busType = DBusConnection.SESSION;
                    }
                    dBusConn = DBusConnection.getConnection(busType);
                    ts = (Signal) dBusConn.getRemoteObject(
                            SIGNAL_BUSNAME, SIGNAL_OBJECTPATH,
                            Signal.class);
                } catch (DBusException e) {
                    e.printStackTrace();
                    if (dBusConn != null) {
                        dBusConn.disconnect();
                    }
                    System.exit(3);
                    return;
                }
            } else {
                String settingsPath = ns.getString("config");
                if (TextUtils.isEmpty(settingsPath)) {
                    settingsPath = System.getProperty("user.home") + "/.config/signal";
                    if (!new File(settingsPath).exists()) {
                        String legacySettingsPath = System.getProperty("user.home") + "/.config/textsecure";
                        if (new File(legacySettingsPath).exists()) {
                            settingsPath = legacySettingsPath;
                        }
                    }
                }

                m = new Manager(username, settingsPath);
                ts = m;
                if (m.userExists()) {
                    try {
                        m.load();
                    } catch (Exception e) {
                        System.err.println("Error loading state file \"" + m.getFileName() + "\": " + e.getMessage());
                        System.exit(2);
                        return;
                    }
                }
            }

            switch (ns.getString("command")) {
                case "register":
                    if (dBusConn != null) {
                        System.err.println("register is not yet implemented via dbus");
                        System.exit(1);
                    }
                    if (!m.userHasKeys()) {
                        m.createNewIdentity();
                    }
                    try {
                        m.register(ns.getBoolean("voice"));
                    } catch (IOException e) {
                        System.err.println("Request verify error: " + e.getMessage());
                        System.exit(3);
                    }
                    break;
                case "verify":
                    if (dBusConn != null) {
                        System.err.println("verify is not yet implemented via dbus");
                        System.exit(1);
                    }
                    if (!m.userHasKeys()) {
                        System.err.println("User has no keys, first call register.");
                        System.exit(1);
                    }
                    if (m.isRegistered()) {
                        System.err.println("User registration is already verified");
                        System.exit(1);
                    }
                    try {
                        m.verifyAccount(ns.getString("verificationCode"));
                    } catch (IOException e) {
                        System.err.println("Verify error: " + e.getMessage());
                        System.exit(3);
                    }
                    break;
                case "link":
                    if (dBusConn != null) {
                        System.err.println("link is not yet implemented via dbus");
                        System.exit(1);
                    }

                    // When linking, username is null and we always have to create keys
                    m.createNewIdentity();

                    String deviceName = ns.getString("name");
                    if (deviceName == null) {
                        deviceName = "cli";
                    }
                    try {
                        System.out.println(m.getDeviceLinkUri());
                        m.finishDeviceLink(deviceName);
                        System.out.println("Associated with: " + m.getUsername());
                    } catch (TimeoutException e) {
                        System.err.println("Link request timed out, please try again.");
                        System.exit(3);
                    } catch (IOException e) {
                        System.err.println("Link request error: " + e.getMessage());
                        System.exit(3);
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                        System.exit(3);
                    } catch (UserAlreadyExists e) {
                        System.err.println("The user " + e.getUsername() + " already exists\nDelete \"" + e.getFileName() + "\" before trying again.");
                        System.exit(3);
                    }
                    break;
                case "addDevice":
                    if (dBusConn != null) {
                        System.err.println("link is not yet implemented via dbus");
                        System.exit(1);
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        System.exit(1);
                    }
                    try {
                        m.addDeviceLink(new URI(ns.getString("uri")));
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(3);
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                        System.exit(2);
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        System.exit(2);
                    }
                    break;
                case "listDevices":
                    if (dBusConn != null) {
                        System.err.println("listDevices is not yet implemented via dbus");
                        System.exit(1);
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        System.exit(1);
                    }
                    try {
                        List<DeviceInfo> devices = m.getLinkedDevices();
                        for (DeviceInfo d : devices) {
                            System.out.println("Device " + d.getId() + (d.getId() == m.getDeviceId() ? " (this device)" : "") + ":");
                            System.out.println(" Name: " + d.getName());
                            System.out.println(" Created: " + d.getCreated());
                            System.out.println(" Last seen: " + d.getLastSeen());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(3);
                    }
                    break;
                case "removeDevice":
                    if (dBusConn != null) {
                        System.err.println("removeDevice is not yet implemented via dbus");
                        System.exit(1);
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        System.exit(1);
                    }
                    try {
                        int deviceId = ns.getInt("deviceId");
                        m.removeLinkedDevices(deviceId);
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(3);
                    }
                    break;
                case "send":
                    if (dBusConn == null && !m.isRegistered()) {
                        System.err.println("User is not registered.");
                        System.exit(1);
                    }

                    if (ns.getBoolean("endsession")) {
                        if (ns.getList("recipient") == null) {
                            System.err.println("No recipients given");
                            System.err.println("Aborting sending.");
                            System.exit(1);
                        }
                        try {
                            ts.sendEndSessionMessage(ns.<String>getList("recipient"));
                        } catch (IOException e) {
                            handleIOException(e);
                        } catch (EncapsulatedExceptions e) {
                            handleEncapsulatedExceptions(e);
                        } catch (AssertionError e) {
                            handleAssertionError(e);
                        } catch (DBusExecutionException e) {
                            handleDBusExecutionException(e);
                        } catch (UntrustedIdentityException e) {
                            e.printStackTrace();
                        }
                    } else {
                        String messageText = ns.getString("message");
                        if (messageText == null) {
                            try {
                                messageText = IOUtils.toString(System.in);
                            } catch (IOException e) {
                                System.err.println("Failed to read message from stdin: " + e.getMessage());
                                System.err.println("Aborting sending.");
                                System.exit(1);
                            }
                        }

                        try {
                            List<String> attachments = ns.getList("attachment");
                            if (attachments == null) {
                                attachments = new ArrayList<>();
                            }
                            if (ns.getString("group") != null) {
                                byte[] groupId = decodeGroupId(ns.getString("group"));
                                ts.sendGroupMessage(messageText, attachments, groupId);
                            } else {
                                ts.sendMessage(messageText, attachments, ns.<String>getList("recipient"));
                            }
                        } catch (IOException e) {
                            handleIOException(e);
                        } catch (EncapsulatedExceptions e) {
                            handleEncapsulatedExceptions(e);
                        } catch (AssertionError e) {
                            handleAssertionError(e);
                        } catch (GroupNotFoundException e) {
                            handleGroupNotFoundException(e);
                        } catch (AttachmentInvalidException e) {
                            System.err.println("Failed to add attachment: " + e.getMessage());
                            System.err.println("Aborting sending.");
                            System.exit(1);
                        } catch (DBusExecutionException e) {
                            handleDBusExecutionException(e);
                        } catch (UntrustedIdentityException e) {
                            e.printStackTrace();
                        }
                    }

                    break;
                case "receive":
                    if (dBusConn != null) {
                        try {
                            dBusConn.addSigHandler(Signal.MessageReceived.class, new DBusSigHandler<Signal.MessageReceived>() {
                                @Override
                                public void handle(Signal.MessageReceived s) {
                                    System.out.print(String.format("Envelope from: %s\nTimestamp: %d\nBody: %s\n",
                                            s.getSender(), s.getTimestamp(), s.getMessage()));
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
                        } catch (DBusException e) {
                            e.printStackTrace();
                        }
                        while (true) {
                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException e) {
                                System.exit(0);
                            }
                        }
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        System.exit(1);
                    }
                    int timeout = 5;
                    if (ns.getInt("timeout") != null) {
                        timeout = ns.getInt("timeout");
                    }
                    boolean returnOnTimeout = true;
                    if (timeout < 0) {
                        returnOnTimeout = false;
                        timeout = 3600;
                    }
                    try {
                        m.receiveMessages(timeout, returnOnTimeout, new ReceiveMessageHandler(m));
                    } catch (IOException e) {
                        System.err.println("Error while receiving messages: " + e.getMessage());
                        System.exit(3);
                    } catch (AssertionError e) {
                        handleAssertionError(e);
                    }
                    break;
                case "quitGroup":
                    if (dBusConn != null) {
                        System.err.println("quitGroup is not yet implemented via dbus");
                        System.exit(1);
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        System.exit(1);
                    }

                    try {
                        m.sendQuitGroupMessage(decodeGroupId(ns.getString("group")));
                    } catch (IOException e) {
                        handleIOException(e);
                    } catch (EncapsulatedExceptions e) {
                        handleEncapsulatedExceptions(e);
                    } catch (AssertionError e) {
                        handleAssertionError(e);
                    } catch (GroupNotFoundException e) {
                        handleGroupNotFoundException(e);
                    } catch (UntrustedIdentityException e) {
                        e.printStackTrace();
                    }

                    break;
                case "updateGroup":
                    if (dBusConn != null) {
                        System.err.println("updateGroup is not yet implemented via dbus");
                        System.exit(1);
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        System.exit(1);
                    }

                    try {
                        byte[] groupId = null;
                        if (ns.getString("group") != null) {
                            groupId = decodeGroupId(ns.getString("group"));
                        }
                        byte[] newGroupId = m.sendUpdateGroupMessage(groupId, ns.getString("name"), ns.<String>getList("member"), ns.getString("avatar"), ns.getString("quit")!=null);
                        if (groupId == null) {
                            System.out.println("Creating new group \"" + Base64.encodeBytes(newGroupId) + "\" …");
                        }
                    } catch (IOException e) {
                        handleIOException(e);
                    } catch (AttachmentInvalidException e) {
                        System.err.println("Failed to add avatar attachment for group\": " + e.getMessage());
                        System.err.println("Aborting sending.");
                        System.exit(1);
                    } catch (GroupNotFoundException e) {
                        handleGroupNotFoundException(e);
                    } catch (EncapsulatedExceptions e) {
                        handleEncapsulatedExceptions(e);
                    } catch (UntrustedIdentityException e) {
                        e.printStackTrace();
                    }

                    break;
                case "daemon":
                    if (dBusConn != null) {
                        System.err.println("Stop it.");
                        System.exit(1);
                    }
                    if (!m.isRegistered()) {
                        System.err.println("User is not registered.");
                        System.exit(1);
                    }
                    DBusConnection conn = null;
                    try {
                        try {
                            int busType;
                            if (ns.getBoolean("system")) {
                                busType = DBusConnection.SYSTEM;
                            } else {
                                busType = DBusConnection.SESSION;
                            }
                            conn = DBusConnection.getConnection(busType);
                            conn.exportObject(SIGNAL_OBJECTPATH, m);
                            conn.requestBusName(SIGNAL_BUSNAME);
                        } catch (DBusException e) {
                            e.printStackTrace();
                            System.exit(3);
                        }
                        try {
                            m.receiveMessages(3600, false, new DbusReceiveMessageHandler(m, conn));
                        } catch (IOException e) {
                            System.err.println("Error while receiving messages: " + e.getMessage());
                            System.exit(3);
                        } catch (AssertionError e) {
                            handleAssertionError(e);
                        }
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }

                    break;
            }
            System.exit(0);
        } finally {
            if (dBusConn != null) {
                dBusConn.disconnect();
            }
        }
    }

    private static void handleGroupNotFoundException(GroupNotFoundException e) {
        System.err.println("Failed to send to group: " + e.getMessage());
        System.err.println("Aborting sending.");
        System.exit(1);
    }

    private static void handleDBusExecutionException(DBusExecutionException e) {
        System.err.println("Cannot connect to dbus: " + e.getMessage());
        System.err.println("Aborting.");
        System.exit(1);
    }

    private static byte[] decodeGroupId(String groupId) {
        try {
            return Base64.decode(groupId);
        } catch (IOException e) {
            System.err.println("Failed to decode groupId (must be base64) \"" + groupId + "\": " + e.getMessage());
            System.err.println("Aborting sending.");
            System.exit(1);
            return null;
        }
    }

    private static Namespace parseArgs(String[] args) {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("signal-cli")
                .defaultHelp(true)
                .description("Commandline interface for Signal.")
                .version(Manager.PROJECT_NAME + " " + Manager.PROJECT_VERSION);

        parser.addArgument("-v", "--version")
                .help("Show package version.")
                .action(Arguments.version());
        parser.addArgument("--config")
                .help("Set the path, where to store the config (Default: $HOME/.config/signal).");

        MutuallyExclusiveGroup mut = parser.addMutuallyExclusiveGroup();
        mut.addArgument("-u", "--username")
                .help("Specify your phone number, that will be used for verification.");
        mut.addArgument("--dbus")
                .help("Make request via user dbus.")
                .action(Arguments.storeTrue());
        mut.addArgument("--dbus-system")
                .help("Make request via system dbus.")
                .action(Arguments.storeTrue());

        Subparsers subparsers = parser.addSubparsers()
                .title("subcommands")
                .dest("command")
                .description("valid subcommands")
                .help("additional help");

        Subparser parserLink = subparsers.addParser("link");
        parserLink.addArgument("-n", "--name")
                .help("Specify a name to describe this new device.");

        Subparser parserAddDevice = subparsers.addParser("addDevice");
        parserAddDevice.addArgument("--uri")
                .required(true)
                .help("Specify the uri contained in the QR code shown by the new device.");

        Subparser parserDevices = subparsers.addParser("listDevices");

        Subparser parserRemoveDevice = subparsers.addParser("removeDevice");
        parserRemoveDevice.addArgument("-d", "--deviceId")
                .type(int.class)
                .required(true)
                .help("Specify the device you want to remove. Use listDevices to see the deviceIds.");

        Subparser parserRegister = subparsers.addParser("register");
        parserRegister.addArgument("-v", "--voice")
                .help("The verification should be done over voice, not sms.")
                .action(Arguments.storeTrue());

        Subparser parserVerify = subparsers.addParser("verify");
        parserVerify.addArgument("verificationCode")
                .help("The verification code you received via sms or voice call.");

        Subparser parserSend = subparsers.addParser("send");
        parserSend.addArgument("-g", "--group")
                .help("Specify the recipient group ID. If '-m' parameter is not  <CR>CTRL+D<CR> (aka EOF) in order to stop reading from STDIN and send the content");
        parserSend.addArgument("recipient")
                .help("Specify the recipients' phone number.")
                .nargs("*");
        parserSend.addArgument("-m", "--message")
                .help("Specify the message, if missing standard input is used.");
        parserSend.addArgument("-a", "--attachment")
                .nargs("*")
                .help("Add file as attachment");
        parserSend.addArgument("-e", "--endsession")
                .help("Clear session state and send end session message.")
                .action(Arguments.storeTrue());

        Subparser parserLeaveGroup = subparsers.addParser("quitGroup");
        parserLeaveGroup.addArgument("-g", "--group")
                .required(true)
                .help("Specify the recipient group ID.");

        Subparser parserUpdateGroup = subparsers.addParser("updateGroup");
        parserUpdateGroup.addArgument("-g", "--group")
                .help("Specify the recipient group ID.");
        parserUpdateGroup.addArgument("-n", "--name")
                .help("Specify the new group name.");
        parserUpdateGroup.addArgument("-a", "--avatar")
                .help("Specify a new group avatar image file");
        parserUpdateGroup.addArgument("-q", "--quit")
                .help("Quit from the group")
                .action(Arguments.storeTrue());
        parserUpdateGroup.addArgument("-m", "--member")
                .nargs("*")
                .help("Specify one or more members to add to the group");

        Subparser parserReceive = subparsers.addParser("receive");
        parserReceive.addArgument("-t", "--timeout")
                .type(int.class)
                .help("Number of seconds to wait for new messages (negative values disable timeout)");

        Subparser parserDaemon = subparsers.addParser("daemon");
        parserDaemon.addArgument("--system")
                .action(Arguments.storeTrue())
                .help("Use DBus system bus instead of user bus.");

        try {
            Namespace ns = parser.parseArgs(args);
            if ("link".equals(ns.getString("command"))) {
                if (ns.getString("username") != null) {
                    parser.printUsage();
                    System.err.println("You cannot specify a username (phone number) when linking");
                    System.exit(2);
                }
            } else if (!ns.getBoolean("dbus") && !ns.getBoolean("dbus_system")) {
                if (ns.getString("username") == null) {
                    parser.printUsage();
                    System.err.println("You need to specify a username (phone number)");
                    System.exit(2);
                }
                if (!PhoneNumberFormatter.isValidNumber(ns.getString("username"))) {
                    System.err.println("Invalid username (phone number), make sure you include the country code.");
                    System.exit(2);
                }
            }
            if (ns.getList("recipient") != null && !ns.getList("recipient").isEmpty() && ns.getString("group") != null) {
                System.err.println("You cannot specify recipients by phone number and groups a the same time");
                System.exit(2);
            }
            return ns;
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            return null;
        }
    }

    private static void handleAssertionError(AssertionError e) {
        System.err.println("Failed to send/receive message (Assertion): " + e.getMessage());
        e.printStackTrace();
        System.err.println("If you use an Oracle JRE please check if you have unlimited strength crypto enabled, see README");
        System.exit(1);
    }

    private static void handleEncapsulatedExceptions(EncapsulatedExceptions e) {
        System.err.println("Failed to send (some) messages:");
        for (NetworkFailureException n : e.getNetworkExceptions()) {
            System.err.println("Network failure for \"" + n.getE164number() + "\": " + n.getMessage());
        }
        for (UnregisteredUserException n : e.getUnregisteredUserExceptions()) {
            System.err.println("Unregistered user \"" + n.getE164Number() + "\": " + n.getMessage());
        }
        for (UntrustedIdentityException n : e.getUntrustedIdentityExceptions()) {
            System.err.println("Untrusted Identity for \"" + n.getE164Number() + "\": " + n.getMessage());
        }
    }

    private static void handleIOException(IOException e) {
        System.err.println("Failed to send message: " + e.getMessage());
    }

    private static class ReceiveMessageHandler implements Manager.ReceiveMessageHandler {
        final Manager m;

        public ReceiveMessageHandler(Manager m) {
            this.m = m;
        }

        @Override
        public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, GroupInfo group) {
            SignalServiceAddress source = envelope.getSourceAddress();
            System.out.println(String.format("Envelope from: %s (device: %d)", source.getNumber(), envelope.getSourceDevice()));
            if (source.getRelay().isPresent()) {
                System.out.println("Relayed by: " + source.getRelay().get());
            }
            System.out.println("Timestamp: " + envelope.getTimestamp());

            if (envelope.isReceipt()) {
                System.out.println("Got receipt.");
            } else if (envelope.isSignalMessage() | envelope.isPreKeySignalMessage()) {
                if (content == null) {
                    System.out.println("Failed to decrypt message.");
                } else {
                    if (content.getDataMessage().isPresent()) {
                        SignalServiceDataMessage message = content.getDataMessage().get();
                        handleSignalServiceDataMessage(message, group);
                    }
                    if (content.getSyncMessage().isPresent()) {
                        System.out.println("Received a sync message");
                        SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

                        if (syncMessage.getContacts().isPresent()) {
                            System.out.println("Received sync contacts");
                            printAttachment(syncMessage.getContacts().get());
                        }
                        if (syncMessage.getGroups().isPresent()) {
                            System.out.println("Received sync groups");
                            printAttachment(syncMessage.getGroups().get());
                        }
                        if (syncMessage.getRead().isPresent()) {
                            System.out.println("Received sync read messages list");
                            for (ReadMessage rm : syncMessage.getRead().get()) {
                                System.out.println("From: " + rm.getSender() + " Message timestamp: " + rm.getTimestamp());
                            }
                        }
                        if (syncMessage.getRequest().isPresent()) {
                            System.out.println("Received sync request");
                            if (syncMessage.getRequest().get().isContactsRequest()) {
                                System.out.println(" - contacts request");
                            }
                            if (syncMessage.getRequest().get().isGroupsRequest()) {
                                System.out.println(" - groups request");
                            }
                        }
                        if (syncMessage.getSent().isPresent()) {
                            System.out.println("Received sync sent message");
                            final SentTranscriptMessage sentTranscriptMessage = syncMessage.getSent().get();
                            System.out.println("To: " + (sentTranscriptMessage.getDestination().isPresent() ? sentTranscriptMessage.getDestination().get() : "Unknown") + " , Message timestamp: " + sentTranscriptMessage.getTimestamp());
                            SignalServiceDataMessage message = sentTranscriptMessage.getMessage();
                            handleSignalServiceDataMessage(message, null);
                        }
                    }
                }
            } else {
                System.out.println("Unknown message received.");
            }
            System.out.println();
        }

        // TODO remove group parameter
        private void handleSignalServiceDataMessage(SignalServiceDataMessage message, GroupInfo group) {
            System.out.println("Message timestamp: " + message.getTimestamp());

            if (message.getBody().isPresent()) {
                System.out.println("Body: " + message.getBody().get());
            }
            if (message.getGroupInfo().isPresent()) {
                SignalServiceGroup groupInfo = message.getGroupInfo().get();
                System.out.println("Group info:");
                System.out.println("  Id: " + Base64.encodeBytes(groupInfo.getGroupId()));
                if (groupInfo.getName().isPresent()) {
                    System.out.println("  Name: " + groupInfo.getName().get());
                } else if (group != null) {
                    System.out.println("  Name: " + group.name);
                } else {
                    System.out.println("  Name: <Unknown group>");
                }
                System.out.println("  Type: " + groupInfo.getType());
                if (groupInfo.getMembers().isPresent()) {
                    for (String member : groupInfo.getMembers().get()) {
                        System.out.println("  Member: " + member);
                    }
                }
                if (groupInfo.getAvatar().isPresent()) {
                    System.out.println("  Avatar:");
                    printAttachment(groupInfo.getAvatar().get());
                }
            }
            if (message.isEndSession()) {
                System.out.println("Is end session");
            }

            if (message.getAttachments().isPresent()) {
                System.out.println("Attachments: ");
                for (SignalServiceAttachment attachment : message.getAttachments().get()) {
                    printAttachment(attachment);
                }
            }
        }

        private void printAttachment(SignalServiceAttachment attachment) {
            System.out.println("- " + attachment.getContentType() + " (" + (attachment.isPointer() ? "Pointer" : "") + (attachment.isStream() ? "Stream" : "") + ")");
            if (attachment.isPointer()) {
                final SignalServiceAttachmentPointer pointer = attachment.asPointer();
                System.out.println("  Id: " + pointer.getId() + " Key length: " + pointer.getKey().length + (pointer.getRelay().isPresent() ? " Relay: " + pointer.getRelay().get() : ""));
                System.out.println("  Size: " + (pointer.getSize().isPresent() ? pointer.getSize().get() + " bytes" : "<unavailable>") + (pointer.getPreview().isPresent() ? " (Preview is available: " + pointer.getPreview().get().length + " bytes)" : ""));
                File file = m.getAttachmentFile(pointer.getId());
                if (file.exists()) {
                    System.out.println("  Stored plaintext in: " + file);
                }
            }
        }
    }

    private static class DbusReceiveMessageHandler extends ReceiveMessageHandler {
        final DBusConnection conn;

        public DbusReceiveMessageHandler(Manager m, DBusConnection conn) {
            super(m);
            this.conn = conn;
        }

        @Override
        public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, GroupInfo group) {
            super.handleMessage(envelope, content, group);

            if (!envelope.isReceipt() && content != null && content.getDataMessage().isPresent()) {
                SignalServiceDataMessage message = content.getDataMessage().get();

                if (!message.isEndSession() &&
                        !(message.getGroupInfo().isPresent() &&
                                message.getGroupInfo().get().getType() != SignalServiceGroup.Type.DELIVER)) {
                    List<String> attachments = new ArrayList<>();
                    if (message.getAttachments().isPresent()) {
                        for (SignalServiceAttachment attachment : message.getAttachments().get()) {
                            if (attachment.isPointer()) {
                                attachments.add(m.getAttachmentFile(attachment.asPointer().getId()).getAbsolutePath());
                            }
                        }
                    }

                    try {
                        conn.sendSignal(new Signal.MessageReceived(
                                SIGNAL_OBJECTPATH,
                                message.getTimestamp(),
                                envelope.getSource(),
                                message.getGroupInfo().isPresent() ? message.getGroupInfo().get().getGroupId() : new byte[0],
                                message.getBody().isPresent() ? message.getBody().get() : "",
                                attachments));
                    } catch (DBusException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void printAttachment(SignalServiceAttachment attachment) {
            System.out.println("- " + attachment.getContentType() + " (" + (attachment.isPointer() ? "Pointer" : "") + (attachment.isStream() ? "Stream" : "") + ")");
            if (attachment.isPointer()) {
                final SignalServiceAttachmentPointer pointer = attachment.asPointer();
                System.out.println("  Id: " + pointer.getId() + " Key length: " + pointer.getKey().length + (pointer.getRelay().isPresent() ? " Relay: " + pointer.getRelay().get() : ""));
                System.out.println("  Size: " + (pointer.getSize().isPresent() ? pointer.getSize().get() + " bytes" : "<unavailable>") + (pointer.getPreview().isPresent() ? " (Preview is available: " + pointer.getPreview().get().length + " bytes)" : ""));
                File file = m.getAttachmentFile(pointer.getId());
                if (file.exists()) {
                    System.out.println("  Stored plaintext in: " + file);
                }
            }
        }
    }
}
