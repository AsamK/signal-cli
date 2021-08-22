package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
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
import java.util.HashMap;
import java.util.List;

public class UpdateGroupCommand implements DbusCommand, JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(UpdateGroupCommand.class);

    @Override
    public String getName() {
        return "updateGroup";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Create or update a group.");
        subparser.addArgument("-g", "--group-id", "--group").help("Specify the group ID.");
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
                .choices("enabled", "enabled-with-approval", "disabled");

        subparser.addArgument("--set-permission-add-member")
                .help("Set permission to add new group members")
                .choices("every-member", "only-admins");
        subparser.addArgument("--set-permission-edit-details")
                .help("Set permission to edit group details")
                .choices("every-member", "only-admins");
        subparser.addArgument("--set-permission-send-messages")
                .help("Set permission to send messages")
                .choices("every-member", "only-admins");

        subparser.addArgument("-e", "--expiration").type(int.class).help("Set expiration time of messages (seconds)");
    }

    GroupLinkState getGroupLinkState(String value) throws UserErrorException {
        if (value == null) {
            return null;
        }
        switch (value) {
            case "enabled":
                return GroupLinkState.ENABLED;
            case "enabled-with-approval":
            case "enabledWithApproval":
                return GroupLinkState.ENABLED_WITH_APPROVAL;
            case "disabled":
                return GroupLinkState.DISABLED;
            default:
                throw new UserErrorException("Invalid group link state: " + value);
        }
    }

    GroupPermission getGroupPermission(String value) throws UserErrorException {
        if (value == null) {
            return null;
        }
        switch (value) {
            case "every-member":
            case "everyMember":
                return GroupPermission.EVERY_MEMBER;
            case "only-admins":
            case "onlyAdmins":
                return GroupPermission.ONLY_ADMINS;
            default:
                throw new UserErrorException("Invalid group permission: " + value);
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        GroupId groupId = null;
        final var groupIdString = ns.getString("group-id");
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
        var groupLinkState = getGroupLinkState(ns.getString("link"));
        var groupExpiration = ns.getInt("expiration");
        var groupAddMemberPermission = getGroupPermission(ns.getString("set-permission-add-member"));
        var groupEditDetailsPermission = getGroupPermission(ns.getString("set-permission-edit-details"));
        var groupSendMessagesPermission = getGroupPermission(ns.getString("set-permission-send-messages"));

        try {
            boolean isNewGroup = false;
            if (groupId == null) {
                isNewGroup = true;
                var results = m.createGroup(groupName,
                        groupMembers,
                        groupAvatar == null ? null : new File(groupAvatar));
                ErrorUtils.handleSendMessageResults(results.second());
                groupId = results.first();
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
                    groupLinkState,
                    groupAddMemberPermission,
                    groupEditDetailsPermission,
                    groupAvatar == null ? null : new File(groupAvatar),
                    groupExpiration,
                    groupSendMessagesPermission == null
                            ? null
                            : groupSendMessagesPermission == GroupPermission.ONLY_ADMINS);
            Long timestamp = null;
            if (results != null) {
                timestamp = results.first();
                ErrorUtils.handleSendMessageResults(results.second());
            }
            outputResult(outputWriter, timestamp, isNewGroup ? groupId : null);
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
    public void handleCommand(
            final Namespace ns, final Signal signal, final OutputWriter outputWriter
    ) throws CommandException {
        byte[] groupId = null;
        if (ns.getString("group-id") != null) {
            try {
                groupId = Util.decodeGroupId(ns.getString("group-id")).serialize();
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
                outputResult(outputWriter, null, GroupId.unknownVersion(newGroupId));
            }
        } catch (Signal.Error.AttachmentInvalid e) {
            throw new UserErrorException("Failed to add avatar attachment for group\": " + e.getMessage());
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage());
        }
    }

    private void outputResult(final OutputWriter outputWriter, final Long timestamp, final GroupId groupId) {
        if (outputWriter instanceof PlainTextWriter) {
            final var writer = (PlainTextWriter) outputWriter;
            if (groupId != null) {
                writer.println("Created new group: \"{}\"", groupId.toBase64());
            }
            if (timestamp != null) {
                writer.println("{}", timestamp);
            }
        } else {
            final var writer = (JsonWriter) outputWriter;
            final var result = new HashMap<>();
            if (timestamp != null) {
                result.put("timestamp", timestamp);
            }
            if (groupId != null) {
                result.put("groupId", groupId.toBase64());
            }
            writer.write(result);
        }
    }
}
