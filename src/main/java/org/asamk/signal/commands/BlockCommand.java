package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.GroupNotFoundException;
import org.asamk.signal.manager.api.NotPrimaryDeviceException;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BlockCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(BlockCommand.class);

    @Override
    public String getName() {
        return "block";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Block the given contacts or groups (no messages will be received)");
        subparser.addArgument("recipient").help("Contact number").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Group ID").nargs("*");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var contacts = ns.<String>getList("recipient");
        final var recipients = CommandUtil.getSingleRecipientIdentifiers(contacts, m.getSelfNumber());
        try {
            m.setContactsBlocked(recipients, true);
        } catch (NotPrimaryDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        } catch (IOException e) {
            throw new UnexpectedErrorException("Failed to sync block to linked devices: " + e.getMessage(), e);
        } catch (UnregisteredRecipientException e) {
            throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
        }

        final var groupIdStrings = ns.<String>getList("group-id");
        final var groupIds = CommandUtil.getGroupIds(groupIdStrings);
        try {
            m.setGroupsBlocked(groupIds, true);
        } catch (NotPrimaryDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        } catch (GroupNotFoundException e) {
            logger.warn("Unknown group id: {}", e.getMessage());
        } catch (IOException e) {
            throw new UnexpectedErrorException("Failed to sync block to linked devices: " + e.getMessage(), e);
        }
    }
}
