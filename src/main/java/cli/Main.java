/**
 * Copyright (C) 2015 AsamK
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cli;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.apache.commons.io.IOUtils;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.*;
import org.whispersystems.textsecure.api.messages.multidevice.TextSecureSyncMessage;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

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

        ArgumentParser parser = ArgumentParsers.newArgumentParser("textsecure-cli")
                .defaultHelp(true)
                .description("Commandline interface for TextSecure.");
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
        parser.addArgument("-u", "--username")
                .required(true)
                .help("Specify your phone number, that will be used for verification.");
        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        final String username = ns.getString("username");
        final Manager m = new Manager(username);
        if (m.userExists()) {
            try {
                m.load();
            } catch (Exception e) {
                System.out.println("Loading file error: " + e.getMessage());
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
                    System.out.println("Request verify error: " + e.getMessage());
                    System.exit(3);
                }
                break;
            case "verify":
                if (!m.userHasKeys()) {
                    System.out.println("User has no keys, first call register.");
                    System.exit(1);
                }
                if (m.isRegistered()) {
                    System.out.println("User registration is already verified");
                    System.exit(1);
                }
                try {
                    m.verifyAccount(ns.getString("verificationCode"));
                } catch (IOException e) {
                    System.out.println("Verify error: " + e.getMessage());
                    System.exit(3);
                }
                break;
            case "send":
                if (!m.isRegistered()) {
                    System.out.println("User is not registered.");
                    System.exit(1);
                }
                TextSecureMessageSender messageSender = m.getMessageSender();
                String messageText = ns.getString("message");
                if (messageText == null) {
                    try {
                        messageText = IOUtils.toString(System.in);
                    } catch (IOException e) {
                        System.out.println("Failed to read message from stdin: " + e.getMessage());
                        System.exit(1);
                    }
                }
                final TextSecureDataMessage.Builder messageBuilder = TextSecureDataMessage.newBuilder().withBody(messageText);
                final List<String> attachments = ns.<String>getList("attachment");
                if (attachments != null) {
                    List<TextSecureAttachment> textSecureAttachments = new ArrayList<TextSecureAttachment>(attachments.size());
                    for (String attachment : attachments) {
                        try {
                            File attachmentFile = new File(attachment);
                            InputStream attachmentStream = new FileInputStream(attachmentFile);
                            final long attachmentSize = attachmentFile.length();
                            String mime = Files.probeContentType(Paths.get(attachment));
                            textSecureAttachments.add(new TextSecureAttachmentStream(attachmentStream, mime, attachmentSize, null));
                        } catch (IOException e) {
                            System.out.println("Failed to add attachment \"" + attachment + "\": " + e.getMessage());
                            System.exit(1);
                        }
                    }
                    messageBuilder.withAttachments(textSecureAttachments);
                }
                TextSecureDataMessage message = messageBuilder.build();
                for (String recipient : ns.<String>getList("recipient")) {
                    try {
                        messageSender.sendMessage(new TextSecureAddress(recipient), message);
                    } catch (UntrustedIdentityException | IOException e) {
                        System.out.println("Send message: " + e.getMessage());
                    }
                }
                break;
            case "receive":
                if (!m.isRegistered()) {
                    System.out.println("User is not registered.");
                    System.exit(1);
                }
                try {
                    m.receiveMessages(new Manager.ReceiveMessageHandler() {
                        @Override
                        public void handleMessage(TextSecureEnvelope envelope) {
                            System.out.println("Envelope from: " + envelope.getSource());
                            System.out.println("Timestamp: " + envelope.getTimestamp());

                            if (envelope.isReceipt()) {
                                System.out.println("Got receipt.");
                            } else if (envelope.isWhisperMessage() | envelope.isPreKeyWhisperMessage()) {
                                TextSecureContent content = m.decryptMessage(envelope);

                                if (content == null) {
                                    System.out.println("Failed to decrypt message.");
                                } else {
                                    if (content.getDataMessage().isPresent()) {
                                        TextSecureDataMessage message = content.getDataMessage().get();

                                        System.out.println("Body: " + message.getBody().get());
                                        if (message.getAttachments().isPresent()) {
                                            System.out.println("Attachments: ");
                                            for (TextSecureAttachment attachment : message.getAttachments().get()) {
                                                System.out.println("- " + attachment.getContentType() + " (" + (attachment.isPointer() ? "Pointer" : "") + (attachment.isStream() ? "Stream" : "") + ")");
                                                if (attachment.isPointer()) {
                                                    System.out.println("  Id: " + attachment.asPointer().getId() + " Key length: " + attachment.asPointer().getKey().length + (attachment.asPointer().getRelay().isPresent() ? " Relay: " + attachment.asPointer().getRelay().get() : ""));
                                                }
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
                    });
                } catch (IOException e) {
                    System.out.println("Error while receiving message: " + e.getMessage());
                }
                break;
        }
        m.save();
    }
}
