package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;

import java.io.IOException;

import static org.asamk.signal.util.ErrorUtils.handleTimestampAndSendMessageResults;

public class JoinGroupCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--uri").required(true).help("Specify the uri with the group invitation link.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
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
            final var writer = new PlainTextWriterImpl(System.out);

            final var results = m.joinGroup(linkUrl);
            var newGroupId = results.first();
            if (!m.getGroup(newGroupId).isMember(m.getSelfRecipientId())) {
                writer.println("Requested to join group \"{}\"", newGroupId.toBase64());
            } else {
                writer.println("Joined group \"{}\"", newGroupId.toBase64());
            }
            handleTimestampAndSendMessageResults(writer, 0, results.second());
        } catch (GroupPatchNotAcceptedException e) {
            throw new UserErrorException("Failed to join group, maybe already a member");
        } catch (IOException e) {
            throw new IOErrorException("Failed to send message: " + e.getMessage());
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage());
        } catch (GroupLinkNotActiveException e) {
            throw new UserErrorException("Group link is not valid: " + e.getMessage());
        }
    }
}
