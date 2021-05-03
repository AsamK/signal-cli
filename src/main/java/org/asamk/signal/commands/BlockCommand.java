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

public class BlockCommand implements LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(BlockCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("contact").help("Contact number").nargs("*");
        subparser.addArgument("-g", "--group").help("Group ID").nargs("*");
        subparser.help("Block the given contacts or groups (no messages will be received)");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        for (var contact_number : ns.<String>getList("contact")) {
            try {
                m.setContactBlocked(contact_number, true);
            } catch (InvalidNumberException e) {
                logger.warn("Invalid number {}: {}", contact_number, e.getMessage());
            } catch (NotMasterDeviceException e) {
                throw new UserErrorException("This command doesn't work on linked devices.");
            }
        }

        if (ns.<String>getList("group") != null) {
            for (var groupIdString : ns.<String>getList("group")) {
                try {
                    var groupId = Util.decodeGroupId(groupIdString);
                    m.setGroupBlocked(groupId, true);
                } catch (GroupIdFormatException | GroupNotFoundException e) {
                    logger.warn("Invalid group id {}: {}", groupIdString, e.getMessage());
                }
            }
        }
    }
}
