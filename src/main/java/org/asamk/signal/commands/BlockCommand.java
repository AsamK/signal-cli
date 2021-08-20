package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
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

public class BlockCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(BlockCommand.class);

    @Override
    public String getName() {
        return "block";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Block the given contacts or groups (no messages will be received)");
        subparser.addArgument("contact").help("Contact number").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Group ID").nargs("*");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        for (var contactNumber : ns.<String>getList("contact")) {
            try {
                m.setContactBlocked(contactNumber, true);
            } catch (InvalidNumberException e) {
                logger.warn("Invalid number {}: {}", contactNumber, e.getMessage());
            } catch (NotMasterDeviceException e) {
                throw new UserErrorException("This command doesn't work on linked devices.");
            }
        }

        if (ns.<String>getList("group-id") != null) {
            for (var groupIdString : ns.<String>getList("group-id")) {
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
