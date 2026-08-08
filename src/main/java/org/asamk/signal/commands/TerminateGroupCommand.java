package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.GroupNotFoundException;
import org.asamk.signal.manager.api.NotAGroupMemberException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;

import java.io.IOException;

import static org.asamk.signal.util.SendMessageResultUtils.outputResult;

public class TerminateGroupCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "terminateGroup";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help(
                "Permanently terminate a group for all members. Requires admin privileges; afterwards nobody can send messages or start calls.");
        subparser.addArgument("-g", "--group-id", "--group").required(true).help("Specify the group ID.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var groupId = CommandUtil.getGroupId(ns.getString("group-id"));

        try {
            final var results = m.terminateGroup(groupId);
            outputResult(outputWriter, results);
        } catch (IOException e) {
            throw new IOErrorException("Failed to send message: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new UserErrorException("Failed to terminate group: " + e.getMessage());
        }
    }
}
