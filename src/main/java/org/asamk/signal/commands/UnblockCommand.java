package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.NotMasterDeviceException;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class UnblockCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(UnblockCommand.class);

    @Override
    public String getName() {
        return "unblock";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Unblock the given contacts or groups (messages will be received again)");
        subparser.addArgument("recipient").help("Contact number").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Group ID").nargs("*");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        for (var contactNumber : CommandUtil.getSingleRecipientIdentifiers(ns.getList("recipient"),
                m.getSelfNumber())) {
            try {
                m.setContactBlocked(contactNumber, false);
            } catch (NotMasterDeviceException e) {
                throw new UserErrorException("This command doesn't work on linked devices.");
            } catch (IOException e) {
                throw new UnexpectedErrorException("Failed to sync unblock to linked devices: " + e.getMessage(), e);
            } catch (UnregisteredRecipientException e) {
                throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
            }
        }

        final var groupIdStrings = ns.<String>getList("group-id");
        for (var groupId : CommandUtil.getGroupIds(groupIdStrings)) {
            try {
                m.setGroupBlocked(groupId, false);
            } catch (NotMasterDeviceException e) {
                throw new UserErrorException("This command doesn't work on linked devices.");
            } catch (GroupNotFoundException e) {
                logger.warn("Unknown group id: {}", groupId);
            } catch (IOException e) {
                throw new UnexpectedErrorException("Failed to sync unblock to linked devices: " + e.getMessage(), e);
            }
        }
    }
}
