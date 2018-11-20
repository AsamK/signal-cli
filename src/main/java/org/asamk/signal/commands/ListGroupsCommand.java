package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.storage.groups.GroupInfo;
import org.whispersystems.signalservice.internal.util.Base64;

import java.util.List;

public class ListGroupsCommand implements LocalCommand {

    private static void printGroup(GroupInfo group, boolean detailed) {
        if (detailed) {
            System.out.println(String.format("Id: %s Name: %s  Active: %s Members: %s",
                    Base64.encodeBytes(group.groupId), group.name, group.active, group.members));
        } else {
            System.out.println(String.format("Id: %s Name: %s  Active: %s", Base64.encodeBytes(group.groupId),
                    group.name, group.active));
        }
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-d", "--detailed").action(Arguments.storeTrue())
                .help("List members of each group");
        subparser.help("List group name and ids");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        List<GroupInfo> groups = m.getGroups();
        boolean detailed = ns.getBoolean("detailed");

        for (GroupInfo group : groups) {
            printGroup(group, detailed);
        }
        return 0;
    }
}
