package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.GroupId;
import org.asamk.signal.manager.GroupIdFormatException;
import org.asamk.signal.manager.GroupNotFoundException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

public class UnblockCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("contact").help("Contact number").nargs("*");
        subparser.addArgument("-g", "--group").help("Group ID").nargs("*");
        subparser.help("Unblock the given contacts or groups (messages will be received again)");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        for (String contact_number : ns.<String>getList("contact")) {
            try {
                m.setContactBlocked(contact_number, false);
            } catch (InvalidNumberException e) {
                System.err.println(e.getMessage());
            }
        }

        if (ns.<String>getList("group") != null) {
            for (String groupIdString : ns.<String>getList("group")) {
                try {
                    GroupId groupId = Util.decodeGroupId(groupIdString);
                    m.setGroupBlocked(groupId, false);
                } catch (GroupIdFormatException | GroupNotFoundException e) {
                    System.err.println(e.getMessage());
                }
            }
        }

        return 0;
    }
}
