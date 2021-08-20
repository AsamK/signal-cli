package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.manager.Manager;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class ListContactsCommand implements LocalCommand {

    @Override
    public String getName() {
        return "listContacts";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Show a list of known contacts with names.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m, final OutputWriter outputWriter) {
        final var writer = (PlainTextWriter) outputWriter;

        var contacts = m.getContacts();
        for (var c : contacts) {
            writer.println("Number: {} Name: {} Blocked: {}",
                    getLegacyIdentifier(m.resolveSignalServiceAddress(c.first())),
                    c.second().getName(),
                    c.second().isBlocked());
        }
    }
}
