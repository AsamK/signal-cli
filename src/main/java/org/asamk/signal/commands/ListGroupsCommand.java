package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.util.Util;
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

    private static Set<String> resolveMembers(Manager m, Set<RecipientId> addresses) {
        return addresses.stream()
                .map(m::resolveSignalServiceAddress)
                .map(Util::getLegacyIdentifier)
                .collect(Collectors.toSet());
    }

    private static Set<JsonGroupMember> resolveJsonMembers(Manager m, Set<RecipientId> addresses) {
        return addresses.stream()
                .map(m::resolveSignalServiceAddress)
                .map(address -> new JsonGroupMember(address.getNumber().orNull(),
                        address.getUuid().transform(UUID::toString).orNull()))
                .collect(Collectors.toSet());
    }

    private static void printGroupPlainText(
            PlainTextWriter writer, Manager m, GroupInfo group, boolean detailed
    ) {
        if (detailed) {
            final var groupInviteLink = group.getGroupInviteLink();

            writer.println(
                    "Id: {} Name: {} Description: {} Active: {} Blocked: {} Members: {} Pending members: {} Requesting members: {} Admins: {} Link: {}",
                    group.getGroupId().toBase64(),
                    group.getTitle(),
                    group.getDescription(),
                    group.isMember(m.getSelfRecipientId()),
                    group.isBlocked(),
                    resolveMembers(m, group.getMembers()),
                    resolveMembers(m, group.getPendingMembers()),
                    resolveMembers(m, group.getRequestingMembers()),
                    resolveMembers(m, group.getAdminMembers()),
                    groupInviteLink == null ? '-' : groupInviteLink.getUrl());
        } else {
            writer.println("Id: {} Name: {}  Active: {} Blocked: {}",
                    group.getGroupId().toBase64(),
                    group.getTitle(),
                    group.isMember(m.getSelfRecipientId()),
                    group.isBlocked());
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var groups = m.getGroups();

        if (outputWriter instanceof JsonWriter) {
            final var jsonWriter = (JsonWriter) outputWriter;

            var jsonGroups = groups.stream().map(group -> {
                final var groupInviteLink = group.getGroupInviteLink();

                return new JsonGroup(group.getGroupId().toBase64(),
                        group.getTitle(),
                        group.getDescription(),
                        group.isMember(m.getSelfRecipientId()),
                        group.isBlocked(),
                        resolveJsonMembers(m, group.getMembers()),
                        resolveJsonMembers(m, group.getPendingMembers()),
                        resolveJsonMembers(m, group.getRequestingMembers()),
                        resolveJsonMembers(m, group.getAdminMembers()),
                        groupInviteLink == null ? null : groupInviteLink.getUrl());
            }).collect(Collectors.toList());

            jsonWriter.write(jsonGroups);
        } else {
            final var writer = (PlainTextWriter) outputWriter;
            boolean detailed = ns.getBoolean("detailed");
            for (var group : groups) {
                printGroupPlainText(writer, m, group, detailed);
            }
        }
    }

    private static final class JsonGroup {

        public final String id;
        public final String name;
        public final String description;
        public final boolean isMember;
        public final boolean isBlocked;

        public final Set<JsonGroupMember> members;
        public final Set<JsonGroupMember> pendingMembers;
        public final Set<JsonGroupMember> requestingMembers;
        public final Set<JsonGroupMember> admins;
        public final String groupInviteLink;

        public JsonGroup(
                String id,
                String name,
                String description,
                boolean isMember,
                boolean isBlocked,
                Set<JsonGroupMember> members,
                Set<JsonGroupMember> pendingMembers,
                Set<JsonGroupMember> requestingMembers,
                Set<JsonGroupMember> admins,
                String groupInviteLink
        ) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.isMember = isMember;
            this.isBlocked = isBlocked;

            this.members = members;
            this.pendingMembers = pendingMembers;
            this.requestingMembers = requestingMembers;
            this.admins = admins;
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
