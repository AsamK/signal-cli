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
package cli;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.apache.commons.io.IOUtils;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.*;
import org.whispersystems.textsecure.api.messages.multidevice.TextSecureSyncMessage;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.textsecure.api.push.exceptions.NetworkFailureException;
import org.whispersystems.textsecure.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.textsecure.api.util.InvalidNumberException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        // Workaround for BKS truststore
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);

        Namespace ns = parseArgs(args);
        if (ns == null) {
            System.exit(1);
        }

        final String username = ns.getString("username");
        final Manager m = new Manager(username);
        if (m.userExists()) {
            try {
                m.load();
            } catch (Exception e) {
                System.err.println("Error loading state file \"" + m.getFileName() + "\": " + e.getMessage());
                System.exit(2);
            }
        }

        switch (ns.getString("command")) {
            case "register":
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
                if (!m.isRegistered()) {
                    System.err.println("User is not registered.");
                    System.exit(1);
                }
                String messageText = ns.getString("message");
                if (messageText == null) {
                    try {
                        messageText = IOUtils.toString(System.in);
                    } catch (IOException e) {
                        System.err.println("Failed to read message from stdin: " + e.getMessage());
                        System.exit(1);
                    }
                }

                final List<String> attachments = ns.getList("attachment");
                List<TextSecureAttachment> textSecureAttachments = null;
                if (attachments != null) {
                    textSecureAttachments = new ArrayList<>(attachments.size());
                    for (String attachment : attachments) {
                        try {
                            File attachmentFile = new File(attachment);
                            InputStream attachmentStream = new FileInputStream(attachmentFile);
                            final long attachmentSize = attachmentFile.length();
                            String mime = Files.probeContentType(Paths.get(attachment));
                            textSecureAttachments.add(new TextSecureAttachmentStream(attachmentStream, mime, attachmentSize, null));
                        } catch (IOException e) {
                            System.err.println("Failed to add attachment \"" + attachment + "\": " + e.getMessage());
                            System.err.println("Aborting sending.");
                            System.exit(1);
                        }
                    }
                }

                List<TextSecureAddress> recipients = new ArrayList<>(ns.<String>getList("recipient").size());
                for (String recipient : ns.<String>getList("recipient")) {
                    try {
                        recipients.add(m.getPushAddress(recipient));
                    } catch (InvalidNumberException e) {
                        System.err.println("Failed to add recipient \"" + recipient + "\": " + e.getMessage());
                        System.err.println("Aborting sending.");
                        System.exit(1);
                    }
                }
                sendMessage(m, messageText, textSecureAttachments, recipients);
                break;
            case "receive":
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
                    System.err.println("Error while receiving message: " + e.getMessage());
                    System.exit(3);
                } catch (AssertionError e) {
                    System.err.println("Failed to receive message (Assertion): " + e.getMessage());
                    System.err.println(e.getStackTrace());
                    System.err.println("If you use an Oracle JRE please check if you have unlimited strength crypto enabled, see README");
                    System.exit(1);
                }
                break;
        }
        m.save();
        System.exit(0);
    }

    private static Namespace parseArgs(String[] args) {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("textsecure-cli")
                .defaultHelp(true)
                .description("Commandline interface for TextSecure.")
                .version(Manager.PROJECT_NAME + " " + Manager.PROJECT_VERSION);

        parser.addArgument("-u", "--username")
                .help("Specify your phone number, that will be used for verification.");
        parser.addArgument("-v", "--version")
                .help("Show package version.")
                .action(Arguments.version());

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
        parserSend.addArgument("recipient")
                .help("Specify the recipients' phone number.")
                .nargs("*");
        parserSend.addArgument("-m", "--message")
                .help("Specify the message, if missing standard input is used.");
        parserSend.addArgument("-a", "--attachment")
                .nargs("*")
                .help("Add file as attachment");

        Subparser parserReceive = subparsers.addParser("receive");
        parserReceive.addArgument("-t", "--timeout")
                .type(int.class)
                .help("Number of seconds to wait for new messages (negative values disable timeout)");

        try {
            Namespace ns = parser.parseArgs(args);
            if (ns.getString("username") == null) {
                parser.printUsage();
                System.err.println("You need to specify a username (phone number)");
                System.exit(2);
            }
            return ns;
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            return null;
        }
    }

    private static void sendMessage(Manager m, String messageText, List<TextSecureAttachment> textSecureAttachments,
                                    List<TextSecureAddress> recipients) {
        final TextSecureDataMessage.Builder messageBuilder = TextSecureDataMessage.newBuilder().withBody(messageText);
        if (textSecureAttachments != null) {
            messageBuilder.withAttachments(textSecureAttachments);
        }
        TextSecureDataMessage message = messageBuilder.build();

        try {
            m.sendMessage(recipients, message);
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        } catch (EncapsulatedExceptions e) {
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
        } catch (AssertionError e) {
            System.err.println("Failed to send message (Assertion): " + e.getMessage());
            System.err.println(e.getStackTrace());
            System.err.println("If you use an Oracle JRE please check if you have unlimited strength crypto enabled, see README");
            System.exit(1);
        }
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
                            m.handleEndSession(envelope.getSource());
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
                try {
                    File file = m.retrieveAttachment(pointer);
                    System.out.println("  Stored plaintext in: " + file);
                } catch (IOException | InvalidMessageException e) {
                    System.out.println("Failed to retrieve attachment: " + e.getMessage());
                }
            }
        }
    }
}
