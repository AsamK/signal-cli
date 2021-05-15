package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.GroupLinkState;
import org.asamk.signal.GroupPermission;
import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.util.ErrorUtils;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class UpdateGroupCommand implements DbusCommand, LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(UpdateGroupCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-g", "--group").help("Specify the recipient group ID.");
        subparser.addArgument("-n", "--name").help("Specify the new group name.");
        subparser.addArgument("-d", "--description").help("Specify the new group description.");
        subparser.addArgument("-a", "--avatar").help("Specify a new group avatar image file");
        subparser.addArgument("-m", "--member").nargs("*").help("Specify one or more members to add to the group");
        subparser.addArgument("-r", "--remove-member")
                .nargs("*")
                .help("Specify one or more members to remove from the group");
        subparser.addArgument("--admin").nargs("*").help("Specify one or more members to make a group admin");
        subparser.addArgument("--remove-admin")
                .nargs("*")
                .help("Specify one or more members to remove group admin privileges");

        subparser.addArgument("--reset-link")
                .action(Arguments.storeTrue())
                .help("Reset group link and create new link password");
        subparser.addArgument("--link")
                .help("Set group link state, with or without admin approval")
                .type(Arguments.enumStringType(GroupLinkState.class));

        subparser.addArgument("--set-permission-add-member")
                .help("Set permission to add new group members")
                .type(Arguments.enumStringType(GroupPermission.class));
        subparser.addArgument("--set-permission-edit-details")
                .help("Set permission to edit group details")
                .type(Arguments.enumStringType(GroupPermission.class));

        subparser.addArgument("-e", "--expiration").type(int.class).help("Set expiration time of messages (seconds)");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        final var writer = new PlainTextWriterImpl(System.out);
        GroupId groupId = null;
        final var groupIdString = ns.getString("group");
        if (groupIdString != null) {
            try {
                groupId = Util.decodeGroupId(groupIdString);
            } catch (GroupIdFormatException e) {
                throw new UserErrorException("Invalid group id: " + e.getMessage());
            }
        }

        var groupName = ns.getString("name");
        var groupDescription = ns.getString("description");
        var groupMembers = ns.<String>getList("member");
        var groupRemoveMembers = ns.<String>getList("remove-member");
        var groupAdmins = ns.<String>getList("admin");
        var groupRemoveAdmins = ns.<String>getList("remove-admin");
        var groupAvatar = ns.getString("avatar");
        var groupResetLink = ns.getBoolean("reset-link");
        var groupLinkState = ns.<GroupLinkState>get("link");
        var groupExpiration = ns.getInt("expiration");
        var groupAddMemberPermission = ns.<GroupPermission>get("set-permission-add-member");
        var groupEditDetailsPermission = ns.<GroupPermission>get("set-permission-edit-details");

        try {
            if (groupId == null) {
                var results = m.createGroup(groupName,
                        groupMembers,
                        groupAvatar == null ? null : new File(groupAvatar));
                ErrorUtils.handleTimestampAndSendMessageResults(writer, 0, results.second());
                groupId = results.first();
                writer.println("Created new group: \"{}\"", groupId.toBase64());
                groupName = null;
                groupMembers = null;
                groupAvatar = null;
            }

            var results = m.updateGroup(groupId,
                    groupName,
                    groupDescription,
                    groupMembers,
                    groupRemoveMembers,
                    groupAdmins,
                    groupRemoveAdmins,
                    groupResetLink,
                    groupLinkState != null ? groupLinkState.toLinkState() : null,
                    groupAddMemberPermission != null ? groupAddMemberPermission.toManager() : null,
                    groupEditDetailsPermission != null ? groupEditDetailsPermission.toManager() : null,
                    groupAvatar == null ? null : new File(groupAvatar),
                    groupExpiration);
            if (results != null) {
                ErrorUtils.handleTimestampAndSendMessageResults(writer, results.first(), results.second());
            }
        } catch (AttachmentInvalidException e) {
            throw new UserErrorException("Failed to add avatar attachment for group\": " + e.getMessage());
        } catch (GroupNotFoundException e) {
            logger.warn("Unknown group id: {}", groupIdString);
        } catch (NotAGroupMemberException e) {
            logger.warn("You're not a group member");
        } catch (InvalidNumberException e) {
            throw new UserErrorException("Failed to parse member number: " + e.getMessage());
        } catch (IOException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage());
        }
    }

    @Override
    public void handleCommand(final Namespace ns, final Signal signal) throws CommandException {
        final var writer = new PlainTextWriterImpl(System.out);
        byte[] groupId = null;
        if (ns.getString("group") != null) {
            try {
                groupId = Util.decodeGroupId(ns.getString("group")).serialize();
            } catch (GroupIdFormatException e) {
                throw new UserErrorException("Invalid group id: " + e.getMessage());
            }
        }
        if (groupId == null) {
            groupId = new byte[0];
        }

        var groupName = ns.getString("name");
        if (groupName == null) {
            groupName = "";
        }

        List<String> groupMembers = ns.getList("member");
        if (groupMembers == null) {
            groupMembers = new ArrayList<>();
        }

        var groupAvatar = ns.getString("avatar");
        if (groupAvatar == null) {
            groupAvatar = "";
        }

        try {
            var newGroupId = signal.updateGroup(groupId, groupName, groupMembers, groupAvatar);
            if (groupId.length != newGroupId.length) {
                writer.println("Created new group: \"{}\"", Base64.getEncoder().encodeToString(newGroupId));
            }
        } catch (Signal.Error.AttachmentInvalid e) {
            throw new UserErrorException("Failed to add avatar attachment for group\": " + e.getMessage());
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage());
        }
    }
}
