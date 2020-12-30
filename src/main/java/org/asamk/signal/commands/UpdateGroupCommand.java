package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.manager.GroupIdFormatException;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.util.Base64;

import java.util.ArrayList;
import java.util.List;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;
import static org.asamk.signal.util.ErrorUtils.handleGroupIdFormatException;

public class UpdateGroupCommand implements DbusCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-g", "--group").help("Specify the recipient group ID.");
        subparser.addArgument("-n", "--name").help("Specify the new group name.");
        subparser.addArgument("-a", "--avatar").help("Specify a new group avatar image file");
        subparser.addArgument("-m", "--member").nargs("*").help("Specify one or more members to add to the group");
    }

    @Override
    public int handleCommand(final Namespace ns, final Signal signal) {
        if (!signal.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        byte[] groupId = null;
        if (ns.getString("group") != null) {
            try {
                groupId = Util.decodeGroupId(ns.getString("group")).serialize();
            } catch (GroupIdFormatException e) {
                handleGroupIdFormatException(e);
                return 1;
            }
        }
        if (groupId == null) {
            groupId = new byte[0];
        }

        String groupName = ns.getString("name");
        if (groupName == null) {
            groupName = "";
        }

        List<String> groupMembers = ns.getList("member");
        if (groupMembers == null) {
            groupMembers = new ArrayList<>();
        }

        String groupAvatar = ns.getString("avatar");
        if (groupAvatar == null) {
            groupAvatar = "";
        }

        try {
            byte[] newGroupId = signal.updateGroup(groupId, groupName, groupMembers, groupAvatar);
            if (groupId.length != newGroupId.length) {
                System.out.println("Creating new group \"" + Base64.encodeBytes(newGroupId) + "\" …");
            }
            return 0;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        } catch (Signal.Error.AttachmentInvalid e) {
            System.err.println("Failed to add avatar attachment for group\": " + e.getMessage());
            return 1;
        } catch (DBusExecutionException e) {
            System.err.println("Failed to send message: " + e.getMessage());
            return 1;
        }
    }
}
