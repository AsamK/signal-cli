package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;

public class ListContactsCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        var contacts = m.getContacts();
        for (var c : contacts) {
            System.out.println(String.format("Number: %s Name: %s  Blocked: %b", c.number, c.name, c.blocked));
        }
        return 0;
    }
}
