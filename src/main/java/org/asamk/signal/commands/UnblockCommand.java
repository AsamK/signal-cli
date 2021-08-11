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

public class UnblockCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(UnblockCommand.class);

    public UnblockCommand(final OutputWriter outputWriter) {
    }

    public static void attachToSubparser(final Subparser subparser) {
        subparser.help("Unblock the given contacts or groups (messages will be received again)");
        subparser.addArgument("contact").help("Contact number").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Group ID").nargs("*");
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

        if (ns.<String>getList("group-id") != null) {
            for (var groupIdString : ns.<String>getList("group-id")) {
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
