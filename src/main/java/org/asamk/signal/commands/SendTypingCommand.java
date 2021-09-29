package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.UntrustedIdentityException;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.util.CommandUtil;

import java.io.IOException;
import java.util.HashSet;

public class SendTypingCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "sendTyping";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help(
                "Send typing message to trigger a typing indicator for the recipient. Indicator will be shown for 15seconds unless a typing STOP message is sent first.");
        subparser.addArgument("-g", "--group-id", "--group").help("Specify the recipient group ID.").nargs("*");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        subparser.addArgument("-s", "--stop").help("Send a typing STOP message.").action(Arguments.storeTrue());
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var recipientStrings = ns.<String>getList("recipient");
        final var groupIdStrings = ns.<String>getList("group-id");
        final var action = ns.getBoolean("stop") ? TypingAction.STOP : TypingAction.START;

        final var recipientIdentifiers = new HashSet<RecipientIdentifier>();
        if (recipientStrings != null) {
            final var localNumber = m.getSelfNumber();
            recipientIdentifiers.addAll(CommandUtil.getSingleRecipientIdentifiers(recipientStrings, localNumber));
        }
        if (groupIdStrings != null) {
            recipientIdentifiers.addAll(CommandUtil.getGroupIdentifiers(groupIdStrings));
        }

        if (recipientIdentifiers.isEmpty()) {
            throw new UserErrorException("No recipients given");
        }

        try {
            m.sendTypingMessage(action, recipientIdentifiers);
        } catch (IOException | UntrustedIdentityException e) {
            throw new UserErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")");
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new UserErrorException("Failed to send to group: " + e.getMessage());
        }
    }
}
