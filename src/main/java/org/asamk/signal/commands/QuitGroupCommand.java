package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.asamk.signal.util.ErrorUtils.handleSendMessageResults;

public class QuitGroupCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(QuitGroupCommand.class);
    private final OutputWriter outputWriter;

    public QuitGroupCommand(final OutputWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    public static void attachToSubparser(final Subparser subparser) {
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
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        final GroupId groupId;
        try {
            groupId = Util.decodeGroupId(ns.getString("group-id"));
        } catch (GroupIdFormatException e) {
            throw new UserErrorException("Invalid group id: " + e.getMessage());
        }

        var groupAdmins = ns.<String>getList("admin");

        try {
            try {
                final var results = m.sendQuitGroupMessage(groupId,
                        groupAdmins == null ? Set.of() : new HashSet<>(groupAdmins));
                final var timestamp = results.first();
                outputResult(timestamp);
                handleSendMessageResults(results.second());
            } catch (NotAGroupMemberException e) {
                logger.info("User is not a group member");
            }
            if (ns.getBoolean("delete")) {
                logger.debug("Deleting group {}", groupId);
                m.deleteGroup(groupId);
            }
        } catch (IOException e) {
            throw new IOErrorException("Failed to send message: " + e.getMessage());
        } catch (GroupNotFoundException e) {
            throw new UserErrorException("Failed to send to group: " + e.getMessage());
        } catch (InvalidNumberException e) {
            throw new UserErrorException("Failed to parse admin number: " + e.getMessage());
        } catch (LastGroupAdminException e) {
            throw new UserErrorException("You need to specify a new admin with --admin: " + e.getMessage());
        }
    }

    private void outputResult(final long timestamp) {
        if (outputWriter instanceof PlainTextWriter) {
            final var writer = (PlainTextWriter) outputWriter;
            writer.println("{}", timestamp);
        } else {
            final var writer = (JsonWriter) outputWriter;
            writer.write(Map.of("timestamp", timestamp));
        }
    }
}
