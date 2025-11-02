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

public class SendPollCreateCommand implements JsonRpcLocalCommand {

    private static final Logger logger = LoggerFactory.getLogger(SendPollCreateCommand.class);

    @Override
    public String getName() {
        return "sendPollCreate";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Create a poll and send it to another user or group.");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Specify the recipient group ID.").nargs("*");
        subparser.addArgument("-u", "--username").help("Specify the recipient username or username link.").nargs("*");
        subparser.addArgument("--note-to-self").help("Send the message to self").action(Arguments.storeTrue());
        subparser.addArgument("--notify-self")
                .help("If self is part of recipients/groups send a normal message, not a sync message.")
                .action(Arguments.storeTrue());

        subparser.addArgument("-q", "--question").help("Specify the poll question.").required(true);
        subparser.addArgument("--no-multi")
                .action(Arguments.storeTrue())
                .help("Allow only one option to be selected by each recipient.");
        subparser.addArgument("-o", "--option").help("The options for the poll").nargs("+").required(true);
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

        final var question = ns.getString("question");
        final var noMulti = Boolean.TRUE.equals(ns.getBoolean("no-multi"));
        final var options = ns.<String>getList("option");
        if (options.size() < 2) {
            throw new UserErrorException("Poll needs at least tow options");
        }

        try {
            var results = m.sendPollCreateMessage(question, !noMulti, options, recipientIdentifiers, notifySelf);
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
