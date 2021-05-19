package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.manager.Manager;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class ListContactsCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) {
        final var writer = new PlainTextWriterImpl(System.out);

        var contacts = m.getContacts();
        for (var c : contacts) {
            writer.println("Number: {} Name: {} Blocked: {}",
                    getLegacyIdentifier(m.resolveSignalServiceAddress(c.first())),
                    c.second().getName(),
                    c.second().isBlocked());
        }
    }
}
