package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
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
    }

    private static Set<String> resolveMembers(Set<RecipientAddress> addresses) {
        return addresses.stream().map(RecipientAddress::getLegacyIdentifier).collect(Collectors.toSet());
    }

    private static Set<JsonGroupMember> resolveJsonMembers(Set<RecipientAddress> addresses) {
        return addresses.stream()
                .map(address -> new JsonGroupMember(address.getNumber().orElse(null),
                        address.getUuid().map(UUID::toString).orElse(null)))
                .collect(Collectors.toSet());
    }

    private static void printGroupPlainText(
            PlainTextWriter writer, Group group, boolean detailed
    ) {
        if (detailed) {
            final var groupInviteLink = group.getGroupInviteLinkUrl();

            writer.println(
                    "Id: {} Name: {} Description: {} Active: {} Blocked: {} Members: {} Pending members: {} Requesting members: {} Admins: {} Message expiration: {} Link: {}",
                    group.getGroupId().toBase64(),
                    group.getTitle(),
                    group.getDescription(),
                    group.isMember(),
                    group.isBlocked(),
                    resolveMembers(group.getMembers()),
                    resolveMembers(group.getPendingMembers()),
                    resolveMembers(group.getRequestingMembers()),
                    resolveMembers(group.getAdminMembers()),
                    group.getMessageExpirationTimer() == 0 ? "disabled" : group.getMessageExpirationTimer() + "s",
                    groupInviteLink == null ? '-' : groupInviteLink.getUrl());
        } else {
            writer.println("Id: {} Name: {}  Active: {} Blocked: {}",
                    group.getGroupId().toBase64(),
                    group.getTitle(),
                    group.isMember(),
                    group.isBlocked());
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var groups = m.getGroups();

        if (outputWriter instanceof JsonWriter jsonWriter) {

            var jsonGroups = groups.stream().map(group -> {
                final var groupInviteLink = group.getGroupInviteLinkUrl();

                return new JsonGroup(group.getGroupId().toBase64(),
                        group.getTitle(),
                        group.getDescription(),
                        group.isMember(),
                        group.isBlocked(),
                        group.getMessageExpirationTimer(),
                        resolveJsonMembers(group.getMembers()),
                        resolveJsonMembers(group.getPendingMembers()),
                        resolveJsonMembers(group.getRequestingMembers()),
                        resolveJsonMembers(group.getAdminMembers()),
                        group.getPermissionAddMember().name(),
                        group.getPermissionEditDetails().name(),
                        group.getPermissionSendMessage().name(),
                        groupInviteLink == null ? null : groupInviteLink.getUrl());
            }).collect(Collectors.toList());

            jsonWriter.write(jsonGroups);
        } else {
            final var writer = (PlainTextWriter) outputWriter;
            boolean detailed = Boolean.TRUE.equals(ns.getBoolean("detailed"));
            for (var group : groups) {
                printGroupPlainText(writer, group, detailed);
            }
        }
    }

    private static final class JsonGroup {

        public final String id;
        public final String name;
        public final String description;
        public final boolean isMember;
        public final boolean isBlocked;
        public final int messageExpirationTime;

        public final Set<JsonGroupMember> members;
        public final Set<JsonGroupMember> pendingMembers;
        public final Set<JsonGroupMember> requestingMembers;
        public final Set<JsonGroupMember> admins;
        public final String permissionAddMember;
        public final String permissionEditDetails;
        public final String permissionSendMessage;
        public final String groupInviteLink;

        public JsonGroup(
                String id,
                String name,
                String description,
                boolean isMember,
                boolean isBlocked,
                final int messageExpirationTime,
                Set<JsonGroupMember> members,
                Set<JsonGroupMember> pendingMembers,
                Set<JsonGroupMember> requestingMembers,
                Set<JsonGroupMember> admins,
                final String permissionAddMember,
                final String permissionEditDetails,
                final String permissionSendMessage,
                String groupInviteLink
        ) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.isMember = isMember;
            this.isBlocked = isBlocked;
            this.messageExpirationTime = messageExpirationTime;

            this.members = members;
            this.pendingMembers = pendingMembers;
            this.requestingMembers = requestingMembers;
            this.admins = admins;
            this.permissionAddMember = permissionAddMember;
            this.permissionEditDetails = permissionEditDetails;
            this.permissionSendMessage = permissionSendMessage;
            this.groupInviteLink = groupInviteLink;
        }
    }

    private static final class JsonGroupMember {

        public final String number;
        public final String uuid;

        private JsonGroupMember(final String number, final String uuid) {
            this.number = number;
            this.uuid = uuid;
        }
    }
}
