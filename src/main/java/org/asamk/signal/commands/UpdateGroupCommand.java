package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.api.UpdateGroup;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.SendMessageResultUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.stream.Stream;

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
        subparser.addArgument("--ban").nargs("*").help("Specify one or more members to ban from joining the group");
        subparser.addArgument("--unban").nargs("*").help("Specify one or more members to remove from the ban list");

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
        var groupBan = CommandUtil.getSingleRecipientIdentifiers(ns.getList("ban"), localNumber);
        var groupUnban = CommandUtil.getSingleRecipientIdentifiers(ns.getList("unban"), localNumber);
        var groupAvatar = ns.getString("avatar");
        var groupResetLink = Boolean.TRUE.equals(ns.getBoolean("reset-link"));
        var groupLinkState = getGroupLinkState(ns.getString("link"));
        var groupExpiration = ns.getInt("expiration");
        var groupAddMemberPermission = getGroupPermission(ns.getString("set-permission-add-member"));
        var groupEditDetailsPermission = getGroupPermission(ns.getString("set-permission-edit-details"));
        var groupSendMessagesPermission = getGroupPermission(ns.getString("set-permission-send-messages"));

        try {
            boolean isNewGroup = false;
            SendGroupMessageResults groupMessageResults = null;
            if (groupId == null) {
                isNewGroup = true;
                var results = m.createGroup(groupName, groupMembers, groupAvatar);
                groupMessageResults = results.second();
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
                            .withBanMembers(groupBan)
                            .withUnbanMembers(groupUnban)
                            .withResetGroupLink(groupResetLink)
                            .withGroupLinkState(groupLinkState)
                            .withAddMemberPermission(groupAddMemberPermission)
                            .withEditDetailsPermission(groupEditDetailsPermission)
                            .withAvatarFile(groupAvatar)
                            .withExpirationTimer(groupExpiration)
                            .withIsAnnouncementGroup(groupSendMessagesPermission == null
                                    ? null
                                    : groupSendMessagesPermission == GroupPermission.ONLY_ADMINS)
                            .build());
            if (results != null) {
                if (groupMessageResults == null) {
                    groupMessageResults = results;
                } else {
                    groupMessageResults = new SendGroupMessageResults(results.timestamp(),
                            Stream.concat(groupMessageResults.results().stream(), results.results().stream()).toList());
                }
            }
            outputResult(outputWriter, groupMessageResults, isNewGroup ? groupId : null);
        } catch (AttachmentInvalidException e) {
            throw new UserErrorException("Failed to add avatar attachment for group\": " + e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new UserErrorException(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
        } catch (IOException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")", e);
        }
    }

    private void outputResult(
            final OutputWriter outputWriter, final SendGroupMessageResults results, final GroupId groupId
    ) {
        if (outputWriter instanceof PlainTextWriter writer) {
            if (groupId != null) {
                writer.println("Created new group: \"{}\"", groupId.toBase64());
            }
            if (results != null) {
                var errors = SendMessageResultUtils.getErrorMessagesFromSendMessageResults(results.results());
                SendMessageResultUtils.printSendMessageResultErrors(writer, errors);
                writer.println("{}", results.timestamp());
            }
        } else {
            final var writer = (JsonWriter) outputWriter;
            final var response = new HashMap<>();
            if (results != null) {
                response.put("timestamp", results.timestamp());
                var jsonResults = SendMessageResultUtils.getJsonSendMessageResults(results.results());
                response.put("results", jsonResults);
            }
            if (groupId != null) {
                response.put("groupId", groupId.toBase64());
            }
            writer.write(response);
        }
    }
}
