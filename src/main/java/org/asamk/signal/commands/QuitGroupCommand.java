package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.PlainTextWriterImpl;
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
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.asamk.signal.util.ErrorUtils.handleTimestampAndSendMessageResults;

public class QuitGroupCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-g", "--group").required(true).help("Specify the recipient group ID.");
        subparser.addArgument("--admin")
                .nargs("*")
                .help("Specify one or more members to make a group admin, required if you're currently the only admin.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        final var writer = new PlainTextWriterImpl(System.out);

        final GroupId groupId;
        try {
            groupId = Util.decodeGroupId(ns.getString("group"));
        } catch (GroupIdFormatException e) {
            throw new UserErrorException("Invalid group id: " + e.getMessage());
        }

        var groupAdmins = ns.<String>getList("admin");

        try {
            final var results = m.sendQuitGroupMessage(groupId,
                    groupAdmins == null ? Set.of() : new HashSet<>(groupAdmins));
            handleTimestampAndSendMessageResults(writer, results.first(), results.second());
        } catch (IOException e) {
            throw new IOErrorException("Failed to send message: " + e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new UserErrorException("Failed to send to group: " + e.getMessage());
        } catch (InvalidNumberException e) {
            throw new UserErrorException("Failed to parse admin number: " + e.getMessage());
        } catch (LastGroupAdminException e) {
            throw new UserErrorException("You need to specify a new admin with --admin: " + e.getMessage());
        }
    }
}
