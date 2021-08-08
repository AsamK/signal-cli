package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.manager.Manager;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class ListContactsCommand implements LocalCommand {

    private final OutputWriter outputWriter;

    public ListContactsCommand(final OutputWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    public static void attachToSubparser(final Subparser subparser) {
        subparser.help("Show a list of known contacts with names.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) {
        final var writer = (PlainTextWriterImpl) outputWriter;

        var contacts = m.getContacts();
        for (var c : contacts) {
            writer.println("Number: {} Name: {} Blocked: {}",
                    getLegacyIdentifier(m.resolveSignalServiceAddress(c.first())),
                    c.second().getName(),
                    c.second().isBlocked());
        }
    }
}
