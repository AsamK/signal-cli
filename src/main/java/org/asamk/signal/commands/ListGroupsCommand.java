package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.RecipientAddress;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.CommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ListGroupsCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(ListGroupsCommand.class);

    @Override
    public String getName() {
        return "listGroups";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("List group information including names, ids, active status, blocked status and members");
        subparser.addArgument("-d", "--detailed")
                .action(Arguments.storeTrue())
                .help("List the members and group invite links of each group. If output=json, then this is always set");
        subparser.addArgument("-g", "--group-id").help("Specify one or more group IDs to show.").nargs("*");
    }

    private static Set<String> resolveMembers(Set<RecipientAddress> addresses) {
        return addresses.stream().map(RecipientAddress::getLegacyIdentifier).collect(Collectors.toSet());
    }

    private static Set<JsonGroupMember> resolveJsonMembers(Set<RecipientAddress> addresses) {
        return addresses.stream()
                .map(address -> new JsonGroupMember(address.number().orElse(null),
                        address.uuid().map(UUID::toString).orElse(null)))
                .collect(Collectors.toSet());
    }

    private static void printGroupPlainText(
            PlainTextWriter writer, Group group, boolean detailed
    ) {
        if (detailed) {
            final var groupInviteLink = group.groupInviteLinkUrl();

            writer.println(
                    "Id: {} Name: {} Description: {} Active: {} Blocked: {} Members: {} Pending members: {} Requesting members: {} Admins: {} Banned: {} Message expiration: {} Link: {}",
                    group.groupId().toBase64(),
                    group.title(),
                    group.description(),
                    group.isMember(),
                    group.isBlocked(),
                    resolveMembers(group.members()),
                    resolveMembers(group.pendingMembers()),
                    resolveMembers(group.requestingMembers()),
                    resolveMembers(group.adminMembers()),
                    resolveMembers(group.bannedMembers()),
                    group.messageExpirationTimer() == 0 ? "disabled" : group.messageExpirationTimer() + "s",
                    groupInviteLink == null ? '-' : groupInviteLink.getUrl());
        } else {
            writer.println("Id: {} Name: {}  Active: {} Blocked: {}",
                    group.groupId().toBase64(),
                    group.title(),
                    group.isMember(),
                    group.isBlocked());
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        var groups = m.getGroups();

        final var groupIdStrings = ns.<String>getList("group-id");
        final var groupIds = CommandUtil.getGroupIds(groupIdStrings);
        if (groupIds.size() > 0) {
            groups = groups.stream().filter(g -> groupIds.contains(g.groupId())).toList();
        }

        if (outputWriter instanceof JsonWriter jsonWriter) {

            var jsonGroups = groups.stream().map(group -> {
                final var groupInviteLink = group.groupInviteLinkUrl();

                return new JsonGroup(group.groupId().toBase64(),
                        group.title(),
                        group.description(),
                        group.isMember(),
                        group.isBlocked(),
                        group.messageExpirationTimer(),
                        resolveJsonMembers(group.members()),
                        resolveJsonMembers(group.pendingMembers()),
                        resolveJsonMembers(group.requestingMembers()),
                        resolveJsonMembers(group.adminMembers()),
                        resolveJsonMembers(group.bannedMembers()),
                        group.permissionAddMember().name(),
                        group.permissionEditDetails().name(),
                        group.permissionSendMessage().name(),
                        groupInviteLink == null ? null : groupInviteLink.getUrl());
            }).toList();

            jsonWriter.write(jsonGroups);
        } else {
            final var writer = (PlainTextWriter) outputWriter;
            boolean detailed = Boolean.TRUE.equals(ns.getBoolean("detailed"));
            for (var group : groups) {
                printGroupPlainText(writer, group, detailed);
            }
        }
    }

    private record JsonGroup(
            String id,
            String name,
            String description,
            boolean isMember,
            boolean isBlocked,
            int messageExpirationTime,
            Set<JsonGroupMember> members,
            Set<JsonGroupMember> pendingMembers,
            Set<JsonGroupMember> requestingMembers,
            Set<JsonGroupMember> admins,
            Set<JsonGroupMember> banned,
            String permissionAddMember,
            String permissionEditDetails,
            String permissionSendMessage,
            String groupInviteLink
    ) {}

    private record JsonGroupMember(String number, String uuid) {}
}
