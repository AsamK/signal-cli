package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.HashSet;

public class SendTypingCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help(
                "Send typing message to trigger a typing indicator for the recipient. Indicator will be shown for 15seconds unless a typing STOP message is sent first.");
        subparser.addArgument("-g", "--group").help("Specify the recipient group ID.");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        subparser.addArgument("-s", "--stop").help("Send a typing STOP message.").action(Arguments.storeTrue());
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        final var recipients = ns.<String>getList("recipient");
        final var groupIdString = ns.getString("group");

        final var noRecipients = recipients == null || recipients.isEmpty();
        if (noRecipients && groupIdString == null) {
            throw new UserErrorException("No recipients given");
        }
        if (!noRecipients && groupIdString != null) {
            throw new UserErrorException("You cannot specify recipients by phone number and groups at the same time");
        }

        final var action = ns.getBoolean("stop") ? TypingAction.STOP : TypingAction.START;

        GroupId groupId = null;
        if (groupIdString != null) {
            try {
                groupId = Util.decodeGroupId(groupIdString);
            } catch (GroupIdFormatException e) {
                throw new UserErrorException("Invalid group id: " + e.getMessage());
            }
        }

        try {
            if (groupId != null) {
                m.sendGroupTypingMessage(action, groupId);
            } else {
                m.sendTypingMessage(action, new HashSet<>(recipients));
            }
        } catch (IOException | UntrustedIdentityException e) {
            throw new UserErrorException("Failed to send message: " + e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new UserErrorException("Failed to send to group: " + e.getMessage());
        } catch (InvalidNumberException e) {
            throw new UserErrorException("Invalid number: " + e.getMessage());
        }
    }
}
