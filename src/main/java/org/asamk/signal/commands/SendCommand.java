package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.MutuallyExclusiveGroup;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;
import static org.asamk.signal.util.ErrorUtils.handleGroupIdFormatException;

public class SendCommand implements DbusCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        final MutuallyExclusiveGroup mutuallyExclusiveGroup = subparser.addMutuallyExclusiveGroup();
        mutuallyExclusiveGroup.addArgument("-g", "--group").help("Specify the recipient group ID.");
        mutuallyExclusiveGroup.addArgument("--note-to-self")
                .help("Send the message to self without notification.")
                .action(Arguments.storeTrue());

        subparser.addArgument("-m", "--message").help("Specify the message, if missing standard input is used.");
        subparser.addArgument("-a", "--attachment").nargs("*").help("Add file as attachment");
        subparser.addArgument("-e", "--endsession")
                .help("Clear session state and send end session message.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Signal signal) {
        final List<String> recipients = ns.getList("recipient");
        final Boolean isEndSession = ns.getBoolean("endsession");
        final String groupIdString = ns.getString("group");
        final Boolean isNoteToSelf = ns.getBoolean("note_to_self");

        final boolean noRecipients = recipients == null || recipients.isEmpty();
        if ((noRecipients && isEndSession) || (noRecipients && groupIdString == null && !isNoteToSelf)) {
            System.err.println("No recipients given");
            System.err.println("Aborting sending.");
            return 1;
        }
        if (!noRecipients && groupIdString != null) {
            System.err.println("You cannot specify recipients by phone number and groups at the same time");
            return 1;
        }
        if (!noRecipients && isNoteToSelf) {
            System.err.println("You cannot specify recipients by phone number and not to self at the same time");
            return 1;
        }

        if (isEndSession) {
            try {
                signal.sendEndSessionMessage(recipients);
                return 0;
            } catch (AssertionError e) {
                handleAssertionError(e);
                return 1;
            } catch (Signal.Error.UntrustedIdentity e) {
                System.err.println("Failed to send message: " + e.getMessage());
                return 4;
            } catch (DBusExecutionException e) {
                System.err.println("Failed to send message: " + e.getMessage());
                return 2;
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

        List<String> attachments = ns.getList("attachment");
        if (attachments == null) {
            attachments = List.of();
        }

        if (groupIdString != null) {
            try {
                byte[] groupId;
                try {
                    groupId = Util.decodeGroupId(groupIdString).serialize();
                } catch (GroupIdFormatException e) {
                    handleGroupIdFormatException(e);
                    return 1;
                }

                long timestamp = signal.sendGroupMessage(messageText, attachments, groupId);
                System.out.println(timestamp);
                return 0;
            } catch (AssertionError e) {
                handleAssertionError(e);
                return 1;
            } catch (DBusExecutionException e) {
                System.err.println("Failed to send group message: " + e.getMessage());
                return 2;
            }
        }

        if (isNoteToSelf) {
            try {
                long timestamp = signal.sendNoteToSelfMessage(messageText, attachments);
                System.out.println(timestamp);
                return 0;
            } catch (AssertionError e) {
                handleAssertionError(e);
                return 1;
            } catch (Signal.Error.UntrustedIdentity e) {
                System.err.println("Failed to send message: " + e.getMessage());
                return 4;
            } catch (DBusExecutionException e) {
                System.err.println("Failed to send note to self message: " + e.getMessage());
                return 2;
            }
        }

        try {
            long timestamp = signal.sendMessage(messageText, attachments, recipients);
            System.out.println(timestamp);
            return 0;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        } catch (UnknownObject e) {
            System.err.println("Failed to find dbus object, maybe missing the -u flag: " + e.getMessage());
            return 1;
        } catch (Signal.Error.UntrustedIdentity e) {
            System.err.println("Failed to send message: " + e.getMessage());
            return 4;
        } catch (DBusExecutionException e) {
            System.err.println("Failed to send message: " + e.getMessage());
            return 2;
        }
    }
}
