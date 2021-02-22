package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.util.Util;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.List;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;
import static org.asamk.signal.util.ErrorUtils.handleTimestampAndSendMessageResults;

public class SendReactionCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send reaction to a previously received or sent message.");
        subparser.addArgument("-g", "--group").help("Specify the recipient group ID.");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
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
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        final List<String> recipients = ns.getList("recipient");
        final var groupIdString = ns.getString("group");

        final var noRecipients = recipients == null || recipients.isEmpty();
        if (noRecipients && groupIdString == null) {
            throw new UserErrorException("No recipients given");
        }
        if (!noRecipients && groupIdString != null) {
            throw new UserErrorException("You cannot specify recipients by phone number and groups at the same time");
        }

        final var emoji = ns.getString("emoji");
        final boolean isRemove = ns.getBoolean("remove");
        final var targetAuthor = ns.getString("target_author");
        final long targetTimestamp = ns.getLong("target_timestamp");

        final var writer = new PlainTextWriterImpl(System.out);

        final Pair<Long, List<SendMessageResult>> results;

        GroupId groupId = null;
        if (groupId != null) {
            try {
                groupId = Util.decodeGroupId(groupIdString);
            } catch (GroupIdFormatException e) {
                throw new UserErrorException("Invalid group id:" + e.getMessage());
            }
        }

        try {
            if (groupId != null) {
                results = m.sendGroupMessageReaction(emoji, isRemove, targetAuthor, targetTimestamp, groupId);
            } else {
                results = m.sendMessageReaction(emoji, isRemove, targetAuthor, targetTimestamp, recipients);
            }
            handleTimestampAndSendMessageResults(writer, results.first(), results.second());
        } catch (IOException e) {
            throw new IOErrorException("Failed to send message: " + e.getMessage());
        } catch (AssertionError e) {
            handleAssertionError(e);
            throw e;
        } catch (GroupNotFoundException e) {
            throw new UserErrorException("Failed to send to group: " + e.getMessage());
        } catch (NotAGroupMemberException e) {
            throw new UserErrorException("Failed to send to group: " + e.getMessage());
        } catch (InvalidNumberException e) {
            throw new UserErrorException("Invalid number: " + e.getMessage());
        }
    }
}
