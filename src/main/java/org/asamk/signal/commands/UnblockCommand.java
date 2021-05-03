package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

public class UnblockCommand implements LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(UnblockCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("contact").help("Contact number").nargs("*");
        subparser.addArgument("-g", "--group").help("Group ID").nargs("*");
        subparser.help("Unblock the given contacts or groups (messages will be received again)");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        for (var contactNumber : ns.<String>getList("contact")) {
            try {
                m.setContactBlocked(contactNumber, false);
            } catch (InvalidNumberException e) {
                logger.warn("Invalid number: {}", contactNumber);
            } catch (NotMasterDeviceException e) {
                throw new UserErrorException("This command doesn't work on linked devices.");
            }
        }

        if (ns.<String>getList("group") != null) {
            for (var groupIdString : ns.<String>getList("group")) {
                try {
                    var groupId = Util.decodeGroupId(groupIdString);
                    m.setGroupBlocked(groupId, false);
                } catch (GroupIdFormatException e) {
                    logger.warn("Invalid group id: {}", groupIdString);
                } catch (GroupNotFoundException e) {
                    logger.warn("Unknown group id: {}", groupIdString);
                }
            }
        }
    }
}
