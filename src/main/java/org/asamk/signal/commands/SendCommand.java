package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.dbus.DbusAttachment;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SendCommand implements DbusCommand, JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(SendCommand.class);
    private final OutputWriter outputWriter;

    public SendCommand(final OutputWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    public static void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a message to another user or group.");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        final var mutuallyExclusiveGroup = subparser.addMutuallyExclusiveGroup();
        mutuallyExclusiveGroup.addArgument("-g", "--group-id", "--group").help("Specify the recipient group ID.");
        mutuallyExclusiveGroup.addArgument("--note-to-self")
                .help("Send the message to self without notification.")
                .action(Arguments.storeTrue());

        subparser.addArgument("-m", "--message").help("Specify the message, if missing standard input is used.");
        subparser.addArgument("-a", "--attachment").nargs("*").help("Add file as attachment");
        subparser.addArgument("-e", "--end-session", "--endsession")
                .help("Clear session state and send end session message.")
                .action(Arguments.storeTrue());
    }

    @Override
    public void handleCommand(final Namespace ns, final Signal signal) throws CommandException {
        final List<String> recipients = ns.getList("recipient");
        final var isEndSession = ns.getBoolean("end-session");
        final var groupIdString = ns.getString("group-id");
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

        List<String> attachmentNames = ns.getList("attachment");
        if (attachmentNames == null) {
            attachmentNames = List.of();
        }

        ArrayList<DbusAttachment> dBusAttachments = new ArrayList<>();
        if (!attachmentNames.isEmpty()) {
            for (var attachmentName : attachmentNames) {
                DbusAttachment dBusAttachment = new DbusAttachment(attachmentName);
                dBusAttachments.add(dBusAttachment);
            }
        }

        if (groupIdString != null) {
            byte[] groupId;
            try {
                groupId = Util.decodeGroupId(groupIdString).serialize();
            } catch (GroupIdFormatException e) {
                throw new UserErrorException("Invalid group id: " + e.getMessage());
            }

            try {
                var timestamp = signal.sendGroupMessage(messageText, attachmentNames, groupId);
                outputResult(timestamp);
                return;
            } catch (DBusExecutionException e) {
                throw new UnexpectedErrorException("Failed to send group message: " + e.getMessage());
            }
        }

        if (isNoteToSelf) {
            try {
                var timestamp = signal.sendNoteToSelfMessage(messageText, attachmentNames);
                outputResult(timestamp);
                return;
            } catch (Signal.Error.UntrustedIdentity e) {
                throw new UntrustedKeyErrorException("Failed to send note to self message: " + e.getMessage());
            } catch (DBusExecutionException e) {
                throw new UnexpectedErrorException("Failed to send note to self message: " + e.getMessage());
            }
        }

        try {
            var timestamp = signal.sendMessageV2(messageText, dBusAttachments, recipients);
            outputResult(timestamp);
        } catch (UnknownObject e) {
            throw new UserErrorException("Failed to find dbus object, maybe missing the -u flag: " + e.getMessage());
        } catch (Signal.Error.UntrustedIdentity e) {
            throw new UntrustedKeyErrorException("Failed to send message: " + e.getMessage());
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException("Failed to send message, did not find attachment: " + e.getMessage());
        }
    }

    private void outputResult(final long timestamp) {
        if (outputWriter instanceof PlainTextWriter) {
            final var writer = (PlainTextWriter) outputWriter;
            writer.println("{}", timestamp);
        } else {
            final var writer = (JsonWriter) outputWriter;
            writer.write(Map.of("timestamp", timestamp));
        }
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        handleCommand(ns, new DbusSignalImpl(m, null));
    }
}
