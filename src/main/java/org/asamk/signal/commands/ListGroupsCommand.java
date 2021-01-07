package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ListGroupsCommand implements LocalCommand {

    private enum MembersType {
        MEMBERS,
        PENDING_MEMBERS,
        REQUESTING_MEMBERS,
    }

    private static Set<String> getMembersSet(Manager m, GroupInfo group, MembersType type) {
        Set<SignalServiceAddress> members;
        switch (type) {
            case MEMBERS:
                members = group.getMembers();
                break;
            case PENDING_MEMBERS:
                members = group.getPendingMembers();
                break;
            case REQUESTING_MEMBERS:
                members = group.getRequestingMembers();
                break;
            default:
                return Collections.emptySet();
        }

        return members.stream().map(m::resolveSignalServiceAddress)
                .map(SignalServiceAddress::getLegacyIdentifier)
                .collect(Collectors.toSet());
    }

    private static int printGroupsJson(ObjectMapper jsonProcessor, List<?> objects) {
        try {
            jsonProcessor.writeValue(System.out, objects);
            System.out.println();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return 1;
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
                    getMembersSet(m, group, MembersType.MEMBERS),
                    getMembersSet(m, group, MembersType.PENDING_MEMBERS),
                    getMembersSet(m, group, MembersType.REQUESTING_MEMBERS),
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
        subparser.addArgument("-d", "--detailed").action(Arguments.storeTrue()).help("List members of each group");
        subparser.help("List group name and ids");
        subparser.addArgument("-j", "--json")
                .help("Output received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        List<GroupInfo> groups = m.getGroups();
        boolean detailed = ns.getBoolean("detailed");

        if (ns.getBoolean("json")) {
            final ObjectMapper jsonProcessor = new ObjectMapper();
            jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
            jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

            if (detailed) {
                List<JsonGroupDetailed> objects = new ArrayList<>();
                for (GroupInfo group : groups) {
                    final GroupInviteLinkUrl groupInviteLink = group.getGroupInviteLink();

                    objects.add(new JsonGroupDetailed(group.getGroupId().toBase64(),
                            group.getTitle(),
                            group.isMember(m.getSelfAddress()),
                            group.isBlocked(),
                            getMembersSet(m, group, MembersType.MEMBERS),
                            getMembersSet(m, group, MembersType.PENDING_MEMBERS),
                            getMembersSet(m, group, MembersType.REQUESTING_MEMBERS),
                            groupInviteLink == null ? null : groupInviteLink.getUrl()));
                }

                return printGroupsJson(jsonProcessor, objects);
            } else {
                List<JsonGroup> objects = groups.stream().map(
                        group -> new JsonGroup(group.getGroupId().toBase64(),
                                group.getTitle(),
                                group.isMember(m.getSelfAddress()),
                                group.isBlocked()))
                        .collect(Collectors.toList());

                return printGroupsJson(jsonProcessor, objects);
            }
        } else {
            for (GroupInfo group : groups) {
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

        public JsonGroup(String id, String name, boolean isMember, boolean isBlocked) {
            this.id = id;
            this.name = name;
            this.isMember = isMember;
            this.isBlocked = isBlocked;
        }
    }

    private static final class JsonGroupDetailed {

        public String id;
        public String name;
        public boolean isMember;
        public boolean isBlocked;

        public Set<String> members;
        public Set<String> pendingMembers;
        public Set<String> requestingMembers;
        public String groupInviteLink;

        public JsonGroupDetailed(String id, String name, boolean isMember, boolean isBlocked,
                                 Set<String> members, Set<String> pendingMembers,
                                 Set<String> requestingMembers, String groupInviteLink)
        {
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
