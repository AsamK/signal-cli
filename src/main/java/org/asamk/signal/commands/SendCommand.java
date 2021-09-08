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
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.ErrorUtils;
import org.asamk.signal.util.IOUtils;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SendCommand implements DbusCommand, JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(SendCommand.class);

    @Override
    public String getName() {
        return "send";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a message to another user or group.");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Specify the recipient group ID.").nargs("*");
        subparser.addArgument("--note-to-self")
                .help("Send the message to self without notification.")
                .action(Arguments.storeTrue());

        subparser.addArgument("-m", "--message").help("Specify the message, if missing standard input is used.");
        subparser.addArgument("-a", "--attachment").nargs("*").help("Add file as attachment");
        subparser.addArgument("-e", "--end-session", "--endsession")
                .help("Clear session state and send end session message.")
                .action(Arguments.storeTrue());
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var isNoteToSelf = ns.getBoolean("note-to-self");
        final var recipientStrings = ns.<String>getList("recipient");
        final var groupIdStrings = ns.<String>getList("group-id");

        final var recipientIdentifiers = CommandUtil.getRecipientIdentifiers(m,
                isNoteToSelf,
                recipientStrings,
                groupIdStrings);

        final var isEndSession = ns.getBoolean("end-session");
        if (isEndSession) {
            final var singleRecipients = recipientIdentifiers.stream()
                    .filter(r -> r instanceof RecipientIdentifier.Single)
                    .map(RecipientIdentifier.Single.class::cast)
                    .collect(Collectors.toSet());
            if (singleRecipients.isEmpty()) {
                throw new UserErrorException("No recipients given");
            }

            try {
                m.sendEndSessionMessage(singleRecipients);
                return;
            } catch (IOException e) {
                throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                        .getSimpleName() + ")", e);
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

        try {
            var results = m.sendMessage(new Message(messageText, attachments), recipientIdentifiers);
            outputResult(outputWriter, results.getTimestamp());
            ErrorUtils.handleSendMessageResults(results.getResults());
        } catch (AttachmentInvalidException | IOException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")", e);
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new UserErrorException(e.getMessage());
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Signal signal, final OutputWriter outputWriter
    ) throws CommandException {
        final var recipients = ns.<String>getList("recipient");
        final var isEndSession = ns.getBoolean("end-session");
        final var groupIdStrings = ns.<String>getList("group-id");
        final var isNoteToSelf = ns.getBoolean("note-to-self");

        final var noRecipients = recipients == null || recipients.isEmpty();
        final var noGroups = groupIdStrings == null || groupIdStrings.isEmpty();
        if ((noRecipients && isEndSession) || (noRecipients && noGroups && !isNoteToSelf)) {
            throw new UserErrorException("No recipients given");
        }
        if (!noRecipients && !noGroups) {
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
                throw new UntrustedKeyErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                        .getSimpleName() + ")");
            } catch (DBusExecutionException e) {
                throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                        .getSimpleName() + ")", e);
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

        if (!noGroups) {
            final var groupIds = CommandUtil.getGroupIds(groupIdStrings);

            try {
                long timestamp = 0;
                for (final var groupId : groupIds) {
                    timestamp = signal.sendGroupMessage(messageText, attachments, groupId.serialize());
                }
                outputResult(outputWriter, timestamp);
                return;
            } catch (DBusExecutionException e) {
                throw new UnexpectedErrorException("Failed to send group message: " + e.getMessage(), e);
            }
        }

        if (isNoteToSelf) {
            try {
                var timestamp = signal.sendNoteToSelfMessage(messageText, attachments);
                outputResult(outputWriter, timestamp);
                return;
            } catch (Signal.Error.UntrustedIdentity e) {
                throw new UntrustedKeyErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                        .getSimpleName() + ")");
            } catch (DBusExecutionException e) {
                throw new UnexpectedErrorException("Failed to send note to self message: " + e.getMessage(), e);
            }
        }

        try {
            var timestamp = signal.sendMessage(messageText, attachments, recipients);
            outputResult(outputWriter, timestamp);
        } catch (UnknownObject e) {
            throw new UserErrorException("Failed to find dbus object, maybe missing the -u flag: " + e.getMessage());
        } catch (Signal.Error.UntrustedIdentity e) {
            throw new UntrustedKeyErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")");
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")", e);
        }
    }

    private void outputResult(final OutputWriter outputWriter, final long timestamp) {
        if (outputWriter instanceof PlainTextWriter) {
            final var writer = (PlainTextWriter) outputWriter;
            writer.println("{}", timestamp);
        } else {
            final var writer = (JsonWriter) outputWriter;
            writer.write(Map.of("timestamp", timestamp));
        }
    }
}
