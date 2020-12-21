package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.storage.protocol.JsonIdentityKeyStore;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.List;

public class ListIdentitiesCommand implements LocalCommand {

    private static void printIdentityFingerprint(Manager m, JsonIdentityKeyStore.Identity theirId) {
        String digits = Util.formatSafetyNumber(m.computeSafetyNumber(theirId.getAddress(), theirId.getIdentityKey()));
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
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        if (ns.get("number") == null) {
            for (JsonIdentityKeyStore.Identity identity : m.getIdentities()) {
                printIdentityFingerprint(m, identity);
            }
        } else {
            String number = ns.getString("number");
            try {
                List<JsonIdentityKeyStore.Identity> identities = m.getIdentities(number);
                for (JsonIdentityKeyStore.Identity id : identities) {
                    printIdentityFingerprint(m, id);
                }
            } catch (InvalidNumberException e) {
                System.err.println("Invalid number: " + e.getMessage());
            }
        }
        return 0;
    }
}
