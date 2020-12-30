package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.manager.GroupId;
import org.asamk.signal.manager.GroupInviteLinkUrl;
import org.asamk.signal.manager.Manager;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;

import java.io.IOException;
import java.util.List;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;
import static org.asamk.signal.util.ErrorUtils.handleIOException;
import static org.asamk.signal.util.ErrorUtils.handleTimestampAndSendMessageResults;

public class JoinGroupCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--uri").required(true).help("Specify the uri with the group invitation link.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        final GroupInviteLinkUrl linkUrl;
        String uri = ns.getString("uri");
        try {
            linkUrl = GroupInviteLinkUrl.fromUri(uri);
        } catch (GroupInviteLinkUrl.InvalidGroupLinkException e) {
            System.err.println("Group link is invalid: " + e.getMessage());
            return 2;
        } catch (GroupInviteLinkUrl.UnknownGroupLinkVersionException e) {
            System.err.println("Group link was created with an incompatible version: " + e.getMessage());
            return 2;
        }

        if (linkUrl == null) {
            System.err.println("Link is not a signal group invitation link");
            return 2;
        }

        try {
            final Pair<GroupId, List<SendMessageResult>> results = m.joinGroup(linkUrl);
            GroupId newGroupId = results.first();
            if (!m.getGroup(newGroupId).isMember(m.getSelfAddress())) {
                System.out.println("Requested to join group \"" + newGroupId.toBase64() + "\"");
            } else {
                System.out.println("Joined group \"" + newGroupId.toBase64() + "\"");
            }
            return handleTimestampAndSendMessageResults(0, results.second());
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        } catch (GroupPatchNotAcceptedException e) {
            System.err.println("Failed to join group, maybe already a member");
            return 1;
        } catch (IOException e) {
            e.printStackTrace();
            handleIOException(e);
            return 1;
        } catch (Signal.Error.AttachmentInvalid e) {
            System.err.println("Failed to add avatar attachment for group\": " + e.getMessage());
            return 1;
        } catch (DBusExecutionException e) {
            System.err.println("Failed to send message: " + e.getMessage());
            return 1;
        } catch (GroupLinkNotActiveException e) {
            System.err.println("Group link is not valid: " + e.getMessage());
            return 2;
        }
    }
}
