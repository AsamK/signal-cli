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
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.ErrorUtils;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import java.io.IOException;
import java.util.Map;

public class SendReactionCommand implements DbusCommand, JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "sendReaction";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send reaction to a previously received or sent message.");
        subparser.addArgument("-g", "--group-id", "--group").help("Specify the recipient group ID.").nargs("*");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        subparser.addArgument("--note-to-self")
                .help("Send the reaction to self without notification.")
                .action(Arguments.storeTrue());
        subparser.addArgument("-e", "--emoji")
                .required(true)
                .help("Specify the emoji, should be a single unicode grapheme cluster.");
        subparser.addArgument("-a", "--target-author")
                .required(true)
                .help("Specify the number of the author of the message to which to react.");
        subparser.addArgument("-t", "--target-timestamp")
                .required(true)
                .type(long.class)
                .help("Specify the timestamp of the message to which to react.");
        subparser.addArgument("-r", "--remove").help("Remove a reaction.").action(Arguments.storeTrue());
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

        final var emoji = ns.getString("emoji");
        final var isRemove = ns.getBoolean("remove");
        final var targetAuthor = ns.getString("target-author");
        final var targetTimestamp = ns.getLong("target-timestamp");

        try {
            final var results = m.sendMessageReaction(emoji,
                    isRemove,
                    CommandUtil.getSingleRecipientIdentifier(targetAuthor, m.getUsername()),
                    targetTimestamp,
                    recipientIdentifiers);
            outputResult(outputWriter, results.getTimestamp());
            ErrorUtils.handleSendMessageResults(results.getResults());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new UserErrorException(e.getMessage());
        } catch (IOException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage());
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Signal signal, final OutputWriter outputWriter
    ) throws CommandException {
        final var recipients = ns.<String>getList("recipient");
        final var groupIdStrings = ns.<String>getList("group-id");

        final var noRecipients = recipients == null || recipients.isEmpty();
        final var noGroups = groupIdStrings == null || groupIdStrings.isEmpty();
        if (noRecipients && noGroups) {
            throw new UserErrorException("No recipients given");
        }
        if (!noRecipients && !noGroups) {
            throw new UserErrorException("You cannot specify recipients by phone number and groups at the same time");
        }

        final var emoji = ns.getString("emoji");
        final var isRemove = ns.getBoolean("remove");
        final var targetAuthor = ns.getString("target-author");
        final var targetTimestamp = ns.getLong("target-timestamp");

        try {
            long timestamp = 0;
            if (!noGroups) {
                final var groupIds = CommandUtil.getGroupIds(groupIdStrings);
                for (final var groupId : groupIds) {
                    timestamp = signal.sendGroupMessageReaction(emoji,
                            isRemove,
                            targetAuthor,
                            targetTimestamp,
                            groupId.serialize());
                }
            } else {
                timestamp = signal.sendMessageReaction(emoji, isRemove, targetAuthor, targetTimestamp, recipients);
            }
            outputResult(outputWriter, timestamp);
        } catch (UnknownObject e) {
            throw new UserErrorException("Failed to find dbus object, maybe missing the -u flag: " + e.getMessage());
        } catch (Signal.Error.InvalidNumber e) {
            throw new UserErrorException("Invalid number: " + e.getMessage());
        } catch (Signal.Error.GroupNotFound e) {
            throw new UserErrorException("Failed to send to group: " + e.getMessage());
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage());
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
