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
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.util.Util;

import java.io.IOException;

import static org.asamk.signal.util.ErrorUtils.handleTimestampAndSendMessageResults;

public class QuitGroupCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-g", "--group").required(true).help("Specify the recipient group ID.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        final var writer = new PlainTextWriterImpl(System.out);

        final GroupId groupId;
        try {
            groupId = Util.decodeGroupId(ns.getString("group"));
        } catch (GroupIdFormatException e) {
            throw new UserErrorException("Invalid group id:" + e.getMessage());
        }

        try {
            final var results = m.sendQuitGroupMessage(groupId);
            handleTimestampAndSendMessageResults(writer, results.first(), results.second());
        } catch (IOException e) {
            throw new IOErrorException("Failed to send message: " + e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new UserErrorException("Failed to send to group: " + e.getMessage());
        }
    }
}
