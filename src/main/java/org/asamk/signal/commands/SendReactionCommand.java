package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

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

import java.io.IOException;
import java.util.Map;

public class SendReactionCommand implements JsonRpcLocalCommand {

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
        final var isNoteToSelf = Boolean.TRUE.equals(ns.getBoolean("note-to-self"));
        final var recipientStrings = ns.<String>getList("recipient");
        final var groupIdStrings = ns.<String>getList("group-id");

        final var recipientIdentifiers = CommandUtil.getRecipientIdentifiers(m,
                isNoteToSelf,
                recipientStrings,
                groupIdStrings);

        final var emoji = ns.getString("emoji");
        final var isRemove = Boolean.TRUE.equals(ns.getBoolean("remove"));
        final var targetAuthor = ns.getString("target-author");
        final var targetTimestamp = ns.getLong("target-timestamp");

        try {
            final var results = m.sendMessageReaction(emoji,
                    isRemove,
                    CommandUtil.getSingleRecipientIdentifier(targetAuthor, m.getSelfNumber()),
                    targetTimestamp,
                    recipientIdentifiers);
            outputResult(outputWriter, results.timestamp());
            ErrorUtils.handleSendMessageResults(results.results());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new UserErrorException(e.getMessage());
        } catch (IOException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")", e);
        }
    }

    private void outputResult(final OutputWriter outputWriter, final long timestamp) {
        if (outputWriter instanceof PlainTextWriter writer) {
            writer.println("{}", timestamp);
        } else {
            final var writer = (JsonWriter) outputWriter;
            writer.write(Map.of("timestamp", timestamp));
        }
    }
}
