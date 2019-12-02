package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.storage.contacts.ContactInfo;
import java.util.List;

public class ListContactsCommand implements LocalCommand {
    @Override
    public void attachToSubparser(final Subparser subparser) {
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        List<ContactInfo> contacts = m.getContacts();
        for (ContactInfo c : contacts) {
            System.out.println("Contact " + c.number);
            System.out.println(" Name: " + c.name);
        }
        return 0;
    }
}
