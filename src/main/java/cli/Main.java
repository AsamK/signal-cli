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
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

import java.io.IOException;
import java.security.Security;

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

        String username = ns.getString("username");
        Manager m = new Manager(username);
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
                TextSecureMessage message = TextSecureMessage.newBuilder().withBody(messageText).build();
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
                    message = m.receiveMessage();
                    if (message == null) {
                        System.exit(0);
                    } else {
                        System.out.println("Received message: " + message.getBody().get());
                    }
                } catch (IOException | InvalidVersionException e) {
                    System.out.println("Receive message: " + e.getMessage());
                }
                break;
        }
        m.save();
    }
}
