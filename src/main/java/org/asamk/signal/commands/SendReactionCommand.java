package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.manager.Manager;
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
import static org.asamk.signal.util.ErrorUtils.handleGroupIdFormatException;
import static org.asamk.signal.util.ErrorUtils.handleGroupNotFoundException;
import static org.asamk.signal.util.ErrorUtils.handleIOException;
import static org.asamk.signal.util.ErrorUtils.handleInvalidNumberException;
import static org.asamk.signal.util.ErrorUtils.handleNotAGroupMemberException;
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
    public int handleCommand(final Namespace ns, final Manager m) {
        final List<String> recipients = ns.getList("recipient");
        final var groupIdString = ns.getString("group");

        final var noRecipients = recipients == null || recipients.isEmpty();
        if (noRecipients && groupIdString == null) {
            System.err.println("No recipients given");
            System.err.println("Aborting sending.");
            return 1;
        }
        if (!noRecipients && groupIdString != null) {
            System.err.println("You cannot specify recipients by phone number and groups at the same time");
            return 1;
        }

        final var emoji = ns.getString("emoji");
        final boolean isRemove = ns.getBoolean("remove");
        final var targetAuthor = ns.getString("target_author");
        final long targetTimestamp = ns.getLong("target_timestamp");

        try {
            final var writer = new PlainTextWriterImpl(System.out);

            final Pair<Long, List<SendMessageResult>> results;
            if (groupIdString != null) {
                var groupId = Util.decodeGroupId(groupIdString);
                results = m.sendGroupMessageReaction(emoji, isRemove, targetAuthor, targetTimestamp, groupId);
            } else {
                results = m.sendMessageReaction(emoji, isRemove, targetAuthor, targetTimestamp, recipients);
            }
            return handleTimestampAndSendMessageResults(writer, results.first(), results.second());
        } catch (IOException e) {
            handleIOException(e);
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
        } catch (GroupIdFormatException e) {
            handleGroupIdFormatException(e);
            return 1;
        } catch (InvalidNumberException e) {
            handleInvalidNumberException(e);
            return 1;
        }
    }
}
