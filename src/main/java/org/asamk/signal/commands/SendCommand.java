package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

public class SendCommand implements DbusCommand {

    private final static Logger logger = LoggerFactory.getLogger(SendCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        final var mutuallyExclusiveGroup = subparser.addMutuallyExclusiveGroup();
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
    public void handleCommand(final Namespace ns, final Signal signal) throws CommandException {
        final List<String> recipients = ns.getList("recipient");
        final var isEndSession = ns.getBoolean("endsession");
        final var groupIdString = ns.getString("group");
        final var isNoteToSelf = ns.getBoolean("note-to-self");

        final var noRecipients = recipients == null || recipients.isEmpty();
        if ((noRecipients && isEndSession) || (noRecipients && groupIdString == null && !isNoteToSelf)) {
            throw new UserErrorException("No recipients given");
        }
        if (!noRecipients && groupIdString != null) {
            throw new UserErrorException("You cannot specify recipients by phone number and groups at the same time");
        }
        if (!noRecipients && isNoteToSelf) {
            throw new UserErrorException(
                    "You cannot specify recipients by phone number and note to self at the same time");
        }

        if (isEndSession) {
            try {
                signal.sendEndSessionMessage(recipients);
                return;
            } catch (Signal.Error.UntrustedIdentity e) {
                throw new UntrustedKeyErrorException("Failed to send message: " + e.getMessage());
            } catch (DBusExecutionException e) {
                throw new UnexpectedErrorException("Failed to send message: " + e.getMessage());
            }
        }

        var messageText = ns.getString("message");
        if (messageText == null) {
            try {
                messageText = IOUtils.readAll(System.in, Charset.defaultCharset());
            } catch (IOException e) {
                throw new UserErrorException("Failed to read message from stdin: " + e.getMessage());
            }
        }

        List<String> attachments = ns.getList("attachment");
        if (attachments == null) {
            attachments = List.of();
        }

        final var writer = new PlainTextWriterImpl(System.out);

        if (groupIdString != null) {
            byte[] groupId;
            try {
                groupId = Util.decodeGroupId(groupIdString).serialize();
            } catch (GroupIdFormatException e) {
                throw new UserErrorException("Invalid group id: " + e.getMessage());
            }

            try {
                var timestamp = signal.sendGroupMessage(messageText, attachments, groupId);
                writer.println("{}", timestamp);
                return;
            } catch (DBusExecutionException e) {
                throw new UnexpectedErrorException("Failed to send group message: " + e.getMessage());
            }
        }

        if (isNoteToSelf) {
            try {
                var timestamp = signal.sendNoteToSelfMessage(messageText, attachments);
                writer.println("{}", timestamp);
                return;
            } catch (Signal.Error.UntrustedIdentity e) {
                throw new UntrustedKeyErrorException("Failed to send message: " + e.getMessage());
            } catch (DBusExecutionException e) {
                throw new UnexpectedErrorException("Failed to send note to self message: " + e.getMessage());
            }
        }

        try {
            var timestamp = signal.sendMessage(messageText, attachments, recipients);
            writer.println("{}", timestamp);
        } catch (UnknownObject e) {
            throw new UserErrorException("Failed to find dbus object, maybe missing the -u flag: " + e.getMessage());
        } catch (Signal.Error.UntrustedIdentity e) {
            throw new UntrustedKeyErrorException("Failed to send message: " + e.getMessage());
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage());
        }
    }
}
