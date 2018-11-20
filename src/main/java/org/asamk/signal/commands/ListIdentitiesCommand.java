package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.storage.protocol.JsonIdentityKeyStore;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.Util;

import java.util.List;
import java.util.Map;

public class ListIdentitiesCommand implements LocalCommand {

    private static void printIdentityFingerprint(Manager m, String theirUsername, JsonIdentityKeyStore.Identity theirId) {
        String digits = Util.formatSafetyNumber(m.computeSafetyNumber(theirUsername, theirId.getIdentityKey()));
        System.out.println(String.format("%s: %s Added: %s Fingerprint: %s Safety Number: %s", theirUsername,
                theirId.getTrustLevel(), theirId.getDateAdded(), Hex.toStringCondensed(theirId.getFingerprint()), digits));
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-n", "--number")
                .help("Only show identity keys for the given phone number.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        if (ns.get("number") == null) {
            for (Map.Entry<String, List<JsonIdentityKeyStore.Identity>> keys : m.getIdentities().entrySet()) {
                for (JsonIdentityKeyStore.Identity id : keys.getValue()) {
                    printIdentityFingerprint(m, keys.getKey(), id);
                }
            }
        } else {
            String number = ns.getString("number");
            for (JsonIdentityKeyStore.Identity id : m.getIdentities(number)) {
                printIdentityFingerprint(m, number, id);
            }
        }
        return 0;
    }
}
