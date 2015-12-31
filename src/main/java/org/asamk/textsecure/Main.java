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
package org.asamk.textsecure;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.apache.commons.io.IOUtils;
import org.asamk.TextSecure;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.*;
import org.whispersystems.textsecure.api.messages.multidevice.TextSecureSyncMessage;
import org.whispersystems.textsecure.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.textsecure.api.push.exceptions.NetworkFailureException;
import org.whispersystems.textsecure.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static final String TEXTSECURE_BUSNAME = "org.asamk.TextSecure";
    public static final String TEXTSECURE_OBJECTPATH = "/org/asamk/TextSecure";

    public static void main(String[] args) {
        // Workaround for BKS truststore
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);

        Namespace ns = parseArgs(args);
        if (ns == null) {
            System.exit(1);
        }

        final String username = ns.getString("username");
        Manager m;
        TextSecure ts;
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
                    ts = (TextSecure) dBusConn.getRemoteObject(
                            TEXTSECURE_BUSNAME, TEXTSECURE_OBJECTPATH,
                            TextSecure.class);
                } catch (DBusException e) {
                    e.printStackTrace();
                    if (dBusConn != null) {
                        dBusConn.disconnect();
                    }
                    System.exit(3);
                    return;
                }
            } else {
                m = new Manager(username);
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
                        System.err.println("register is not yet implementd via dbus");
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
                        System.err.println("verify is not yet implementd via dbus");
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
                        }
                    }

                    break;
                case "receive":
                    if (dBusConn != null) {
                        System.err.println("receive is not yet implementd via dbus");
                        System.exit(1);
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
                        System.err.println("quitGroup is not yet implementd via dbus");
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
                    }

                    break;
                case "updateGroup":
                    if (dBusConn != null) {
                        System.err.println("updateGroup is not yet implementd via dbus");
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
                        byte[] newGroupId = m.sendUpdateGroupMessage(groupId, ns.getString("name"), ns.<String>getList("member"), ns.getString("avatar"));
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
                            conn.exportObject(TEXTSECURE_OBJECTPATH, m);
                            conn.requestBusName(TEXTSECURE_BUSNAME);
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
        ArgumentParser parser = ArgumentParsers.newArgumentParser("textsecure-cli")
                .defaultHelp(true)
                .description("Commandline interface for TextSecure.")
                .version(Manager.PROJECT_NAME + " " + Manager.PROJECT_VERSION);

        parser.addArgument("-v", "--version")
                .help("Show package version.")
                .action(Arguments.version());

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

        Subparser parserRegister = subparsers.addParser("register");
        parserRegister.addArgument("-v", "--voice")
                .help("The verification should be done over voice, not sms.")
                .action(Arguments.storeTrue());

        Subparser parserVerify = subparsers.addParser("verify");
        parserVerify.addArgument("verificationCode")
                .help("The verification code you received via sms or voice call.");

        Subparser parserSend = subparsers.addParser("send");
        parserSend.addArgument("-g", "--group")
                .help("Specify the recipient group ID.");
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
            if (!ns.getBoolean("dbus") && !ns.getBoolean("dbus_system")) {
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
        System.err.println(e.getStackTrace());
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
        public void handleMessage(TextSecureEnvelope envelope, TextSecureContent content, GroupInfo group) {
            System.out.println("Envelope from: " + envelope.getSource());
            System.out.println("Timestamp: " + envelope.getTimestamp());

            if (envelope.isReceipt()) {
                System.out.println("Got receipt.");
            } else if (envelope.isWhisperMessage() | envelope.isPreKeyWhisperMessage()) {
                if (content == null) {
                    System.out.println("Failed to decrypt message.");
                } else {
                    if (content.getDataMessage().isPresent()) {
                        TextSecureDataMessage message = content.getDataMessage().get();

                        System.out.println("Message timestamp: " + message.getTimestamp());

                        if (message.getBody().isPresent()) {
                            System.out.println("Body: " + message.getBody().get());
                        }
                        if (message.getGroupInfo().isPresent()) {
                            TextSecureGroup groupInfo = message.getGroupInfo().get();
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
                            for (TextSecureAttachment attachment : message.getAttachments().get()) {
                                printAttachment(attachment);
                            }
                        }
                    }
                    if (content.getSyncMessage().isPresent()) {
                        TextSecureSyncMessage syncMessage = content.getSyncMessage().get();
                        System.out.println("Received sync message");
                    }
                }
            } else {
                System.out.println("Unknown message received.");
            }
            System.out.println();
        }

        private void printAttachment(TextSecureAttachment attachment) {
            System.out.println("- " + attachment.getContentType() + " (" + (attachment.isPointer() ? "Pointer" : "") + (attachment.isStream() ? "Stream" : "") + ")");
            if (attachment.isPointer()) {
                final TextSecureAttachmentPointer pointer = attachment.asPointer();
                System.out.println("  Id: " + pointer.getId() + " Key length: " + pointer.getKey().length + (pointer.getRelay().isPresent() ? " Relay: " + pointer.getRelay().get() : ""));
                System.out.println("  Size: " + (pointer.getSize().isPresent() ? pointer.getSize().get() + " bytes" : "<unavailable>") + (pointer.getPreview().isPresent() ? " (Preview is available: " + pointer.getPreview().get().length + " bytes)" : ""));
                File file = m.getAttachmentFile(pointer.getId());
                if (file.exists()) {
                    System.out.println("  Stored plaintext in: " + file);
                }
            }
        }
    }

    private static class DbusReceiveMessageHandler implements Manager.ReceiveMessageHandler {
        final Manager m;
        final DBusConnection conn;

        public DbusReceiveMessageHandler(Manager m, DBusConnection conn) {
            this.m = m;
            this.conn = conn;
        }

        @Override
        public void handleMessage(TextSecureEnvelope envelope, TextSecureContent content, GroupInfo group) {
            System.out.println("Envelope from: " + envelope.getSource());
            System.out.println("Timestamp: " + envelope.getTimestamp());

            if (envelope.isReceipt()) {
                System.out.println("Got receipt.");
            } else if (envelope.isWhisperMessage() | envelope.isPreKeyWhisperMessage()) {
                if (content == null) {
                    System.out.println("Failed to decrypt message.");
                } else {
                    if (content.getDataMessage().isPresent()) {
                        TextSecureDataMessage message = content.getDataMessage().get();

                        System.out.println("Message timestamp: " + message.getTimestamp());

                        if (message.getBody().isPresent()) {
                            System.out.println("Body: " + message.getBody().get());
                        }

                        if (message.getGroupInfo().isPresent()) {
                            TextSecureGroup groupInfo = message.getGroupInfo().get();
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

                        List<String> attachments = new ArrayList<>();
                        if (message.getAttachments().isPresent()) {
                            System.out.println("Attachments: ");
                            for (TextSecureAttachment attachment : message.getAttachments().get()) {
                                if (attachment.isPointer()) {
                                    attachments.add(m.getAttachmentFile(attachment.asPointer().getId()).getAbsolutePath());
                                }
                                printAttachment(attachment);
                            }
                        }
                        if (!message.isEndSession() &&
                                !(message.getGroupInfo().isPresent() && message.getGroupInfo().get().getType() != TextSecureGroup.Type.DELIVER)) {
                            try {
                                conn.sendSignal(new TextSecure.MessageReceived(
                                        TEXTSECURE_OBJECTPATH,
                                        envelope.getSource(),
                                        message.getGroupInfo().isPresent() ? message.getGroupInfo().get().getGroupId() : new byte[0],
                                        message.getBody().isPresent() ? message.getBody().get() : "",
                                        attachments));
                            } catch (DBusException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (content.getSyncMessage().isPresent()) {
                        TextSecureSyncMessage syncMessage = content.getSyncMessage().get();
                        System.out.println("Received sync message");
                    }
                }
            } else {
                System.out.println("Unknown message received.");
            }
            System.out.println();
        }

        private void printAttachment(TextSecureAttachment attachment) {
            System.out.println("- " + attachment.getContentType() + " (" + (attachment.isPointer() ? "Pointer" : "") + (attachment.isStream() ? "Stream" : "") + ")");
            if (attachment.isPointer()) {
                final TextSecureAttachmentPointer pointer = attachment.asPointer();
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
