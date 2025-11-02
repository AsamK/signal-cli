package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.GroupNotFoundException;
import org.asamk.signal.manager.api.GroupSendingNotAllowedException;
import org.asamk.signal.manager.api.NotAGroupMemberException;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.asamk.signal.util.SendMessageResultUtils.outputResult;

public class SendPollVoteCommand implements JsonRpcLocalCommand {

    private static final Logger logger = LoggerFactory.getLogger(SendPollVoteCommand.class);

    @Override
    public String getName() {
        return "sendPollVote";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Vote on a poll and send it to another user or group.");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Specify the recipient group ID.").nargs("*");
        subparser.addArgument("-u", "--username").help("Specify the recipient username or username link.").nargs("*");
        subparser.addArgument("--note-to-self").help("Send the message to self").action(Arguments.storeTrue());
        subparser.addArgument("--notify-self")
                .help("If self is part of recipients/groups send a normal message, not a sync message.")
                .action(Arguments.storeTrue());

        subparser.addArgument("--poll-author").help("Specify the number of the author of the poll message.");
        subparser.addArgument("--poll-timestamp")
                .type(long.class)
                .help("Specify the timestamp of the original poll message.")
                .required(true);
        subparser.addArgument("-o", "--option")
                .type(Integer.class)
                .help("The option indexes of the poll to vote for")
                .nargs("*");
        subparser.addArgument("--vote-count")
                .type(int.class)
                .help("Specify the number of this vote (increase by one for every time you vote).")
                .required(true);
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var notifySelf = Boolean.TRUE.equals(ns.getBoolean("notify-self"));
        final var isNoteToSelf = Boolean.TRUE.equals(ns.getBoolean("note-to-self"));
        final var recipientStrings = ns.<String>getList("recipient");
        final var groupIdStrings = ns.<String>getList("group-id");
        final var usernameStrings = ns.<String>getList("username");

        final var recipientIdentifiers = CommandUtil.getRecipientIdentifiers(m,
                isNoteToSelf,
                recipientStrings,
                groupIdStrings,
                usernameStrings);

        final var selfNumber = m.getSelfNumber();
        final var pollTimestamp = ns.getLong("poll-timestamp");
        final var pollAuthorString = ns.getString("poll-author");
        final var pollAuthor = CommandUtil.getSingleRecipientIdentifier(pollAuthorString, selfNumber);
        final var options = ns.<Integer>getList("option");
        final var voteCount = ns.getInt("vote-count");

        try {
            var results = m.sendPollVoteMessage(pollAuthor,
                    pollTimestamp,
                    options,
                    voteCount,
                    recipientIdentifiers,
                    notifySelf);
            outputResult(outputWriter, results);
        } catch (IOException e) {
            if (e.getMessage().contains("No prekeys available")) {
                throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                        .getSimpleName() + "), maybe one of the devices of the recipient wasn't online for a while.",
                        e);
            } else {
                throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                        .getSimpleName() + ")", e);
            }
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new UserErrorException(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
        }
    }
}
