package org.asamk.signal.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;

class JsonInterface {

    public String commandName;
    public String recipient;
    public String content;
    public JsonNode details;
}

class InputReader implements Runnable {

    private volatile boolean alive = true;
    private final Manager m;

    InputReader(final Manager m) {
        this.m = m;
    }

    public void terminate() {
        this.alive = false;
    }

    @Override
    public void run() {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        ObjectMapper jsonProcessor = new ObjectMapper();
        while (alive) {
            try {
                String in = br.readLine();
                if (in != null) {
                    JsonInterface command = jsonProcessor.readValue(in, JsonInterface.class);
                    if (command.commandName.equals("sendMessage")) {
                        List<String> recipients = new ArrayList<String>();
                        recipients.add(command.recipient);
                        List<String> attachments = new ArrayList<>();
                        if (command.details != null && command.details.has("attachments")) {
                            command.details.get("attachments").forEach(attachment -> {
                                if (attachment.isTextual()) {
                                    attachments.add(attachment.asText());
                                }
                            });
                        }
                        try {
                            // verbosity flag? better yet, json acknowledgement with timestamp or message id?
                            System.out.println("sentMessage '" + command.content + "' to " + command.recipient);
                            this.m.sendMessage(command.content, attachments, recipients);
                        } catch (AssertionError | AttachmentInvalidException | InvalidNumberException e) {
                            System.err.println("error in sending message");
                            e.printStackTrace(System.out);
                        }
                    } /* elif (command.commandName == "sendTyping") {
        			 getMessageSender().sendTyping(signalServiceAddress?, ....)
        			}*/
                }

            } catch (IOException e) {
                System.err.println(e);
                alive = false;
            }

        }
    }
}

public class StdioCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--json") // should maybe remove this
                .help("Output received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) {
        boolean ignoreAttachments = ns.getBoolean("ignore_attachments");
        InputReader reader = new InputReader(m);
        Thread readerThread = new Thread(reader);
        readerThread.start();
        try {
            m.receiveMessages(1,
                    TimeUnit.HOURS,
                    false,
                    ignoreAttachments,
                    ns.getBoolean("json") ? new JsonReceiveMessageHandler(m) : new ReceiveMessageHandler(m)
                    /*true*/);
        } catch (IOException e) {
            System.err.println("Error while receiving messages: " + e.getMessage());
        } catch (AssertionError e) {
            handleAssertionError(e);
        } finally {
            reader.terminate();
        }
    }
}
