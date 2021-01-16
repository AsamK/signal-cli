package org.asamk.signal.commands;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ListGroupsCommand implements LocalCommand {

    private static Set<String> resolveMembers(Manager m, Set<SignalServiceAddress> addresses) {
        return addresses.stream()
                .map(m::resolveSignalServiceAddress)
                .map(SignalServiceAddress::getLegacyIdentifier)
                .collect(Collectors.toSet());
    }

    private static int printGroupsJson(ObjectMapper jsonProcessor, List<?> objects) {
        try {
            jsonProcessor.writeValue(System.out, objects);
            System.out.println();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return 3;
        }

        return 0;
    }

    private static void printGroupPlainText(Manager m, GroupInfo group, boolean detailed) {
        if (detailed) {
            final GroupInviteLinkUrl groupInviteLink = group.getGroupInviteLink();

            System.out.println(String.format(
                    "Id: %s Name: %s  Active: %s Blocked: %b Members: %s Pending members: %s Requesting members: %s Link: %s",
                    group.getGroupId().toBase64(),
                    group.getTitle(),
                    group.isMember(m.getSelfAddress()),
                    group.isBlocked(),
                    resolveMembers(m, group.getMembers()),
                    resolveMembers(m, group.getPendingMembers()),
                    resolveMembers(m, group.getRequestingMembers()),
                    groupInviteLink == null ? '-' : groupInviteLink.getUrl()));
        } else {
            System.out.println(String.format("Id: %s Name: %s  Active: %s Blocked: %b",
                    group.getGroupId().toBase64(),
                    group.getTitle(),
                    group.isMember(m.getSelfAddress()),
                    group.isBlocked()));
        }
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-d", "--detailed")
                .action(Arguments.storeTrue())
                .help("List the members and group invite links of each group. If output=json, then this is always set");

        subparser.help("List group information including names, ids, active status, blocked status and members");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (ns.get("output") == OutputType.JSON) {
            final ObjectMapper jsonProcessor = new ObjectMapper();
            jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
            jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

            List<JsonGroup> objects = new ArrayList<>();
            for (GroupInfo group : m.getGroups()) {
                final GroupInviteLinkUrl groupInviteLink = group.getGroupInviteLink();

                objects.add(new JsonGroup(group.getGroupId().toBase64(),
                        group.getTitle(),
                        group.isMember(m.getSelfAddress()),
                        group.isBlocked(),
                        resolveMembers(m, group.getMembers()),
                        resolveMembers(m, group.getPendingMembers()),
                        resolveMembers(m, group.getRequestingMembers()),
                        groupInviteLink == null ? null : groupInviteLink.getUrl()));
            }
            return printGroupsJson(jsonProcessor, objects);
        } else {
            boolean detailed = ns.getBoolean("detailed");
            for (GroupInfo group : m.getGroups()) {
                printGroupPlainText(m, group, detailed);
            }
        }

        return 0;
    }

    private static final class JsonGroup {

        public String id;
        public String name;
        public boolean isMember;
        public boolean isBlocked;

        public Set<String> members;
        public Set<String> pendingMembers;
        public Set<String> requestingMembers;
        public String groupInviteLink;

        public JsonGroup(
                String id,
                String name,
                boolean isMember,
                boolean isBlocked,
                Set<String> members,
                Set<String> pendingMembers,
                Set<String> requestingMembers,
                String groupInviteLink
        ) {
            this.id = id;
            this.name = name;
            this.isMember = isMember;
            this.isBlocked = isBlocked;

            this.members = members;
            this.pendingMembers = pendingMembers;
            this.requestingMembers = requestingMembers;
            this.groupInviteLink = groupInviteLink;
        }
    }
}
