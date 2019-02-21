package org.asamk.signal.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import netscape.javascript.JSException;
import org.asamk.Signal;
import org.asamk.signal.*;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.asamk.signal.util.ErrorUtils.*;

public class SendCommand implements DbusCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-g", "--group")
                .help("Specify the recipient group ID.");
        subparser.addArgument("recipient")
                .help("Specify the recipients' phone number.")
                .nargs("*");
        subparser.addArgument("-m", "--message")
                .help("Specify the message, if missing standard input is used.");
        subparser.addArgument("-a", "--attachment")
                .nargs("*")
                .help("Add file as attachment");
        subparser.addArgument("-e", "--endsession")
                .help("Clear session state and send end session message.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Signal signal) {
        if (!signal.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        if ((ns.getList("recipient") == null || ns.getList("recipient").size() == 0) && (ns.getBoolean("endsession") || ns.getString("group") == null)) {
            System.err.println("No recipients given");
            System.err.println("Aborting sending.");
            return 1;
        }

        if (ns.getBoolean("endsession")) {
            try {
                signal.sendEndSessionMessage(ns.<String>getList("recipient"));
                return 0;
            } catch (IOException e) {
                handleIOException(e);
                return 3;
            } catch (EncapsulatedExceptions e) {
                handleEncapsulatedExceptions(e);
                return 3;
            } catch (AssertionError e) {
                handleAssertionError(e);
                return 1;
            } catch (DBusExecutionException e) {
                handleDBusExecutionException(e);
                return 1;
            }
        }

        String messageText = ns.getString("message");
        if (messageText == null) {
            try {
                messageText = IOUtils.readAll(System.in, Charset.defaultCharset());
            } catch (IOException e) {
                System.err.println("Failed to read message from stdin: " + e.getMessage());
                System.err.println("Aborting sending.");
                return 1;
            }
        }

        try {
            List<String> attachments = ns.getList("attachment");
            if (attachments == null) {
                attachments = new ArrayList<>();
            }

            // check to see if message is a JSON
            // { "message": "string", "attachments": [ "path to file", ... ] }
            if (messageText.startsWith("{")) {
                JsonSendableMessage result = new ObjectMapper().readValue(messageText, JsonSendableMessage.class);
                messageText = result.getMessage();
                attachments = result.getAttachments();
            }

            if (ns.getString("group") != null) {
                byte[] groupId = Util.decodeGroupId(ns.getString("group"));
                signal.sendGroupMessage(messageText, attachments, groupId);
            } else {
                signal.sendMessage(messageText, attachments, ns.<String>getList("recipient"));
            }
            return 0;
        } catch (IOException e) {
            handleIOException(e);
            return 3;
        } catch (EncapsulatedExceptions e) {
            handleEncapsulatedExceptions(e);
            return 3;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        } catch (GroupNotFoundException e) {
            handleGroupNotFoundException(e);
            return 1;
        } catch (NotAGroupMemberException e) {
            handleNotAGroupMemberException(e);
            return 1;
        } catch (AttachmentInvalidException e) {
            System.err.println("Failed to add attachment: " + e.getMessage());
            System.err.println("Aborting sending.");
            return 1;
        } catch (DBusExecutionException e) {
            handleDBusExecutionException(e);
            return 1;
        } catch (GroupIdFormatException e) {
            handleGroupIdFormatException(e);
            return 1;
        } catch (JSException e) {
            // not sure how to deal with this.
            e.printStackTrace();
            return 3;
        }
    }
}
