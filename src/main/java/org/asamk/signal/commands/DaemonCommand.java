package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.DbusReceiveMessageHandler;
import org.asamk.signal.JsonDbusReceiveMessageHandler;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import static org.asamk.signal.DbusConfig.SIGNAL_BUSNAME;
import static org.asamk.signal.DbusConfig.SIGNAL_OBJECTPATH;
import static org.asamk.signal.util.ErrorUtils.handleAssertionError;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class JsonInterface {
    public String commandName;
    public String recipient;
    public String content;
    public JsonNode details;
}


class InputReader implements Runnable {
    private volatile boolean alive = true;
	private Manager m;

    public void terminate() {
		this.alive = false;
    }
	InputReader (final Manager m) {
    	this.m = m;
	}

    @Override
    public void run() {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        ObjectMapper jsonProcessor = new ObjectMapper();
		while (alive) {
			try {
    			String in  = br.readLine();
    			if (in != null) { 
                    JsonInterface command = jsonProcessor.readValue(in, JsonInterface.class);
					if (command.commandName.equals("sendMessage")){
            			List<String> recipients = new ArrayList<String>();
            			recipients.add(command.recipient);
            			List<String> attachments = new ArrayList<>();
						if (command.details != null && command.details.has("attachments") ) {
                			command.details.get("attachments").forEach(attachment -> {
                               if (attachment.isTextual()){
                        			attachments.add(attachment.asText());
                               }
                			});
						}
            			try {
                			// verbosity flag? better yet, json acknowledgement with timestamp or message id?
                			System.out.println("sentMessage '" + command.content + "' to " + command.recipient);
                			this.m.sendMessage(command.content, attachments, recipients);
                        } catch (AssertionError | EncapsulatedExceptions| AttachmentInvalidException | InvalidNumberException e) {
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

public class DaemonCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--system")
                .action(Arguments.storeTrue())
                .help("Use DBus system bus instead of user bus.");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--json")
                .help("Output received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
/*      DBusConnection conn = null;
        try {
            try {
                DBusConnection.DBusBusType busType;
                if (ns.getBoolean("system")) {
                    busType = DBusConnection.DBusBusType.SYSTEM;
                } else {
                    busType = DBusConnection.DBusBusType.SESSION;
                }
//                conn = DBusConnection.getConnection(busType);
//                conn.exportObject(SIGNAL_OBJECTPATH, new DbusSignalImpl(m));
//                conn.requestBusName(SIGNAL_BUSNAME);
            } catch (UnsatisfiedLinkError e) {
                System.err.println("Missing native library dependency for dbus service: " + e.getMessage());
                return 1;
            } catch (DBusException e) {
                e.printStackTrace();
                return 2;
            }
            */
            boolean ignoreAttachments = ns.getBoolean("ignore_attachments");
            InputReader reader = new InputReader(m);
            Thread readerThread = new Thread(reader);
            readerThread.start();
            try {
                m.receiveMessagesAndReadStdin(1, TimeUnit.HOURS, false, ignoreAttachments,
                                  ns.getBoolean("json")
                                  ? new JsonReceiveMessageHandler(m)
                                  : new ReceiveMessageHandler(m)
                					/*true*/);
                return 0;
            } catch (IOException e) {
                System.err.println("Error while receiving messages: " + e.getMessage());
                return 3;
            } catch (AssertionError e) {
                handleAssertionError(e);
                return 1;
            } finally {
                reader.terminate();
            }

    }
}
