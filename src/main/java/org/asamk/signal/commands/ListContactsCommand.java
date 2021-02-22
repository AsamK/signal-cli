package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.manager.Manager;

import java.io.IOException;

public class ListContactsCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        final var writer = new PlainTextWriterImpl(System.out);

        var contacts = m.getContacts();
        try {
            for (var c : contacts) {
                writer.println("Number: {} Name: {} Blocked: {}", c.number, c.name, c.blocked);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 3;
        }
        return 0;
    }
}
