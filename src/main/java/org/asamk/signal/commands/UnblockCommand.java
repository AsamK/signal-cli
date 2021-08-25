package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.util.CommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnblockCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(UnblockCommand.class);

    @Override
    public String getName() {
        return "unblock";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Unblock the given contacts or groups (messages will be received again)");
        subparser.addArgument("contact").help("Contact number").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Group ID").nargs("*");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        for (var contactNumber : CommandUtil.getSingleRecipientIdentifiers(ns.getList("contact"), m.getUsername())) {
            try {
                m.setContactBlocked(contactNumber, false);
            } catch (NotMasterDeviceException e) {
                throw new UserErrorException("This command doesn't work on linked devices.");
            }
        }

        final var groupIdStrings = ns.<String>getList("group-id");
        for (var groupId : CommandUtil.getGroupIds(groupIdStrings)) {
            try {
                m.setGroupBlocked(groupId, false);
            } catch (GroupNotFoundException e) {
                logger.warn("Unknown group id: {}", groupId);
            }
        }
    }
}
