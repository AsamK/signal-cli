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
import java.util.stream.Collectors;

public class ListGroupsCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(ListGroupsCommand.class);

    public static void attachToSubparser(final Subparser subparser) {
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

    private final OutputWriter outputWriter;

    public ListGroupsCommand(final OutputWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
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
                        resolveMembers(m, group.getMembers()),
                        resolveMembers(m, group.getPendingMembers()),
                        resolveMembers(m, group.getRequestingMembers()),
                        resolveMembers(m, group.getAdminMembers()),
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

        public String id;
        public String name;
        public String description;
        public boolean isMember;
        public boolean isBlocked;

        public Set<String> members;
        public Set<String> pendingMembers;
        public Set<String> requestingMembers;
        public Set<String> admins;
        public String groupInviteLink;

        public JsonGroup(
                String id,
                String name,
                String description,
                boolean isMember,
                boolean isBlocked,
                Set<String> members,
                Set<String> pendingMembers,
                Set<String> requestingMembers,
                Set<String> admins,
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
}
