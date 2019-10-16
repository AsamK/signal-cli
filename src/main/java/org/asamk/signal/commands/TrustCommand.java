package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.MutuallyExclusiveGroup;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Hex;

import java.util.Locale;

public class TrustCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("number")
                .help("Specify the phone number, for which to set the trust.")
                .required(true);
        MutuallyExclusiveGroup mutTrust = subparser.addMutuallyExclusiveGroup();
        mutTrust.addArgument("-a", "--trust-all-known-keys")
                .help("Trust all known keys of this user, only use this for testing.")
                .action(Arguments.storeTrue());
        mutTrust.addArgument("-v", "--verified-fingerprint")
                .help("Specify the fingerprint of the key, only use this option if you have verified the fingerprint.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        String number = ns.getString("number");
        if (ns.getBoolean("trust_all_known_keys")) {
            boolean res = m.trustIdentityAllKeys(number);
            if (!res) {
                System.err.println("Failed to set the trust for this number, make sure the number is correct.");
                return 1;
            }
        } else {
            String fingerprint = ns.getString("verified_fingerprint");
            if (fingerprint != null) {
                fingerprint = fingerprint.replaceAll(" ", "");
                if (fingerprint.length() == 66) {
                    byte[] fingerprintBytes;
                    try {
                        fingerprintBytes = Hex.toByteArray(fingerprint.toLowerCase(Locale.ROOT));
                    } catch (Exception e) {
                        System.err.println("Failed to parse the fingerprint, make sure the fingerprint is a correctly encoded hex string without additional characters.");
                        return 1;
                    }
                    boolean res = m.trustIdentityVerified(number, fingerprintBytes);
                    if (!res) {
                        System.err.println("Failed to set the trust for the fingerprint of this number, make sure the number and the fingerprint are correct.");
                        return 1;
                    }
                } else if (fingerprint.length() == 60) {
                    boolean res = m.trustIdentityVerifiedSafetyNumber(number, fingerprint);
                    if (!res) {
                        System.err.println("Failed to set the trust for the safety number of this phone number, make sure the phone number and the safety number are correct.");
                        return 1;
                    }
                } else {
                    System.err.println("Fingerprint has invalid format, either specify the old hex fingerprint or the new safety number");
                    return 1;
                }
            } else {
                System.err.println("You need to specify the fingerprint you have verified with -v FINGERPRINT");
                return 1;
            }
        }
        return 0;
    }
}
