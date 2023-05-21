package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.GroupNotFoundException;
import org.asamk.signal.manager.api.LastGroupAdminException;
import org.asamk.signal.manager.api.NotAGroupMemberException;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.asamk.signal.util.SendMessageResultUtils.outputResult;

public class QuitGroupCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(QuitGroupCommand.class);

    @Override
    public String getName() {
        return "quitGroup";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a quit group message to all group members and remove self from member list.");
        subparser.addArgument("-g", "--group-id", "--group").required(true).help("Specify the recipient group ID.");
        subparser.addArgument("--delete")
                .action(Arguments.storeTrue())
                .help("Delete local group data completely after quitting group.");
        subparser.addArgument("--admin")
                .nargs("*")
                .help("Specify one or more members to make a group admin, required if you're currently the only admin.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var groupId = CommandUtil.getGroupId(ns.getString("group-id"));

        var groupAdmins = CommandUtil.getSingleRecipientIdentifiers(ns.getList("admin"), m.getSelfNumber());

        try {
            try {
                final var results = m.quitGroup(groupId, groupAdmins);
                outputResult(outputWriter, results);
            } catch (NotAGroupMemberException e) {
                logger.info("User is not a group member");
            }
            if (Boolean.TRUE.equals(ns.getBoolean("delete"))) {
                logger.debug("Deleting group {}", groupId);
                m.deleteGroup(groupId);
            }
        } catch (IOException e) {
            throw new IOErrorException("Failed to send message: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        } catch (GroupNotFoundException e) {
            throw new UserErrorException("Failed to send to group: " + e.getMessage());
        } catch (LastGroupAdminException e) {
            throw new UserErrorException("You need to specify a new admin with --admin: " + e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
        }
    }
}
