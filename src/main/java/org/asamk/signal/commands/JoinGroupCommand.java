package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.InactiveGroupLinkException;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;

import java.io.IOException;
import java.util.Map;

import static org.asamk.signal.util.ErrorUtils.handleSendMessageResults;

public class JoinGroupCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "joinGroup";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Join a group via an invitation link.");
        subparser.addArgument("--uri").required(true).help("Specify the uri with the group invitation link.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final GroupInviteLinkUrl linkUrl;
        var uri = ns.getString("uri");
        try {
            linkUrl = GroupInviteLinkUrl.fromUri(uri);
        } catch (GroupInviteLinkUrl.InvalidGroupLinkException e) {
            throw new UserErrorException("Group link is invalid: " + e.getMessage());
        } catch (GroupInviteLinkUrl.UnknownGroupLinkVersionException e) {
            throw new UserErrorException("Group link was created with an incompatible version: " + e.getMessage());
        }

        if (linkUrl == null) {
            throw new UserErrorException("Link is not a signal group invitation link");
        }

        try {
            final var results = m.joinGroup(linkUrl);
            var newGroupId = results.first();
            if (outputWriter instanceof JsonWriter writer) {
                if (!m.getGroup(newGroupId).isMember()) {
                    writer.write(Map.of("groupId", newGroupId.toBase64(), "onlyRequested", true));
                } else {
                    writer.write(Map.of("groupId", newGroupId.toBase64()));
                }
            } else {
                final var writer = (PlainTextWriter) outputWriter;
                if (!m.getGroup(newGroupId).isMember()) {
                    writer.println("Requested to join group \"{}\"", newGroupId.toBase64());
                } else {
                    writer.println("Joined group \"{}\"", newGroupId.toBase64());
                }
            }
            handleSendMessageResults(results.second().results());
        } catch (GroupPatchNotAcceptedException e) {
            throw new UserErrorException("Failed to join group, maybe already a member");
        } catch (IOException e) {
            throw new IOErrorException("Failed to send message: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")", e);
        } catch (InactiveGroupLinkException e) {
            throw new UserErrorException("Group link is not valid: " + e.getMessage());
        }
    }
}
