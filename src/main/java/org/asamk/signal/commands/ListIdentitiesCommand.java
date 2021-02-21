package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.storage.protocol.IdentityInfo;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

public class ListIdentitiesCommand implements LocalCommand {

    private static void printIdentityFingerprint(Manager m, IdentityInfo theirId) {
        var digits = Util.formatSafetyNumber(m.computeSafetyNumber(theirId.getAddress(), theirId.getIdentityKey()));
        System.out.println(String.format("%s: %s Added: %s Fingerprint: %s Safety Number: %s",
                theirId.getAddress().getNumber().orNull(),
                theirId.getTrustLevel(),
                theirId.getDateAdded(),
                Hex.toString(theirId.getFingerprint()),
                digits));
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-n", "--number").help("Only show identity keys for the given phone number.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (ns.get("number") == null) {
            for (var identity : m.getIdentities()) {
                printIdentityFingerprint(m, identity);
            }
        } else {
            var number = ns.getString("number");
            try {
                var identities = m.getIdentities(number);
                for (var id : identities) {
                    printIdentityFingerprint(m, id);
                }
            } catch (InvalidNumberException e) {
                System.err.println("Invalid number: " + e.getMessage());
            }
        }
        return 0;
    }
}
