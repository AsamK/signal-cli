package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.UpdateGroup;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.ErrorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class UpdateGroupCommand implements JsonRpcLocalCommand {

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
        return switch (value) {
            case "enabled" -> GroupLinkState.ENABLED;
            case "enabled-with-approval", "enabledWithApproval" -> GroupLinkState.ENABLED_WITH_APPROVAL;
            case "disabled" -> GroupLinkState.DISABLED;
            default -> throw new UserErrorException("Invalid group link state: " + value);
        };
    }

    GroupPermission getGroupPermission(String value) throws UserErrorException {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "every-member", "everyMember" -> GroupPermission.EVERY_MEMBER;
            case "only-admins", "onlyAdmins" -> GroupPermission.ONLY_ADMINS;
            default -> throw new UserErrorException("Invalid group permission: " + value);
        };
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var groupIdString = ns.getString("group-id");
        var groupId = CommandUtil.getGroupId(groupIdString);

        final var localNumber = m.getSelfNumber();

        var groupName = ns.getString("name");
        var groupDescription = ns.getString("description");
        var groupMembers = CommandUtil.getSingleRecipientIdentifiers(ns.getList("member"), localNumber);
        var groupRemoveMembers = CommandUtil.getSingleRecipientIdentifiers(ns.getList("remove-member"), localNumber);
        var groupAdmins = CommandUtil.getSingleRecipientIdentifiers(ns.getList("admin"), localNumber);
        var groupRemoveAdmins = CommandUtil.getSingleRecipientIdentifiers(ns.getList("remove-admin"), localNumber);
        var groupAvatar = ns.getString("avatar");
        var groupResetLink = Boolean.TRUE.equals(ns.getBoolean("reset-link"));
        var groupLinkState = getGroupLinkState(ns.getString("link"));
        var groupExpiration = ns.getInt("expiration");
        var groupAddMemberPermission = getGroupPermission(ns.getString("set-permission-add-member"));
        var groupEditDetailsPermission = getGroupPermission(ns.getString("set-permission-edit-details"));
        var groupSendMessagesPermission = getGroupPermission(ns.getString("set-permission-send-messages"));

        try {
            boolean isNewGroup = false;
            Long timestamp = null;
            if (groupId == null) {
                isNewGroup = true;
                var results = m.createGroup(groupName,
                        groupMembers,
                        groupAvatar == null ? null : new File(groupAvatar));
                timestamp = results.second().timestamp();
                ErrorUtils.handleSendMessageResults(results.second().results());
                groupId = results.first();
                groupName = null;
                groupMembers = null;
                groupAvatar = null;
            }

            var results = m.updateGroup(groupId,
                    UpdateGroup.newBuilder()
                            .withName(groupName)
                            .withDescription(groupDescription)
                            .withMembers(groupMembers)
                            .withRemoveMembers(groupRemoveMembers)
                            .withAdmins(groupAdmins)
                            .withRemoveAdmins(groupRemoveAdmins)
                            .withResetGroupLink(groupResetLink)
                            .withGroupLinkState(groupLinkState)
                            .withAddMemberPermission(groupAddMemberPermission)
                            .withEditDetailsPermission(groupEditDetailsPermission)
                            .withAvatarFile(groupAvatar == null ? null : new File(groupAvatar))
                            .withExpirationTimer(groupExpiration)
                            .withIsAnnouncementGroup(groupSendMessagesPermission == null
                                    ? null
                                    : groupSendMessagesPermission == GroupPermission.ONLY_ADMINS)
                            .build());
            if (results != null) {
                timestamp = results.timestamp();
                ErrorUtils.handleSendMessageResults(results.results());
            }
            outputResult(outputWriter, timestamp, isNewGroup ? groupId : null);
        } catch (AttachmentInvalidException e) {
            throw new UserErrorException("Failed to add avatar attachment for group\": " + e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new UserErrorException(e.getMessage());
        } catch (IOException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")", e);
        }
    }

    private void outputResult(final OutputWriter outputWriter, final Long timestamp, final GroupId groupId) {
        if (outputWriter instanceof PlainTextWriter writer) {
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
