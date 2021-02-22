package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.storage.protocol.IdentityInfo;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;

public class ListIdentitiesCommand implements LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(ListIdentitiesCommand.class);

    private static void printIdentityFingerprint(PlainTextWriter writer, Manager m, IdentityInfo theirId) {
        var digits = Util.formatSafetyNumber(m.computeSafetyNumber(theirId.getAddress(), theirId.getIdentityKey()));
        try {
            writer.println("{}: {} Added: {} Fingerprint: {} Safety Number: {}",
                    theirId.getAddress().getNumber().orNull(),
                    theirId.getTrustLevel(),
                    theirId.getDateAdded(),
                    Hex.toString(theirId.getFingerprint()),
                    digits);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-n", "--number").help("Only show identity keys for the given phone number.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        final var writer = new PlainTextWriterImpl(System.out);

        if (ns.get("number") == null) {
            for (var identity : m.getIdentities()) {
                printIdentityFingerprint(writer, m, identity);
            }
        } else {
            var number = ns.getString("number");
            try {
                var identities = m.getIdentities(number);
                for (var id : identities) {
                    printIdentityFingerprint(writer, m, id);
                }
            } catch (InvalidNumberException e) {
                System.err.println("Invalid number: " + e.getMessage());
            }
        }
        return 0;
    }
}
