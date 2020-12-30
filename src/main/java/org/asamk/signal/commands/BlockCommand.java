package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.GroupId;
import org.asamk.signal.manager.GroupIdFormatException;
import org.asamk.signal.manager.GroupNotFoundException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

public class BlockCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("contact").help("Contact number").nargs("*");
        subparser.addArgument("-g", "--group").help("Group ID").nargs("*");
        subparser.help("Block the given contacts or groups (no messages will be received)");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        for (String contact_number : ns.<String>getList("contact")) {
            try {
                m.setContactBlocked(contact_number, true);
            } catch (InvalidNumberException e) {
                System.err.println(e.getMessage());
            }
        }

        if (ns.<String>getList("group") != null) {
            for (String groupIdString : ns.<String>getList("group")) {
                try {
                    GroupId groupId = Util.decodeGroupId(groupIdString);
                    m.setGroupBlocked(groupId, true);
                } catch (GroupIdFormatException | GroupNotFoundException e) {
                    System.err.println(e.getMessage());
                }
            }
        }

        return 0;
    }
}
