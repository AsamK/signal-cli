package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.ErrorUtils;
import org.asamk.signal.util.Hex;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.Locale;

public class TrustCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("number").help("Specify the phone number, for which to set the trust.").required(true);
        var mutTrust = subparser.addMutuallyExclusiveGroup();
        mutTrust.addArgument("-a", "--trust-all-known-keys")
                .help("Trust all known keys of this user, only use this for testing.")
                .action(Arguments.storeTrue());
        mutTrust.addArgument("-v", "--verified-safety-number", "--verified-fingerprint")
                .help("Specify the safety number of the key, only use this option if you have verified the safety number.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        var number = ns.getString("number");
        if (ns.getBoolean("trust_all_known_keys")) {
            var res = m.trustIdentityAllKeys(number);
            if (!res) {
                System.err.println("Failed to set the trust for this number, make sure the number is correct.");
                return 1;
            }
        } else {
            var safetyNumber = ns.getString("verified_safety_number");
            if (safetyNumber != null) {
                safetyNumber = safetyNumber.replaceAll(" ", "");
                if (safetyNumber.length() == 66) {
                    byte[] fingerprintBytes;
                    try {
                        fingerprintBytes = Hex.toByteArray(safetyNumber.toLowerCase(Locale.ROOT));
                    } catch (Exception e) {
                        System.err.println(
                                "Failed to parse the fingerprint, make sure the fingerprint is a correctly encoded hex string without additional characters.");
                        return 1;
                    }
                    boolean res;
                    try {
                        res = m.trustIdentityVerified(number, fingerprintBytes);
                    } catch (InvalidNumberException e) {
                        ErrorUtils.handleInvalidNumberException(e);
                        return 1;
                    }
                    if (!res) {
                        System.err.println(
                                "Failed to set the trust for the fingerprint of this number, make sure the number and the fingerprint are correct.");
                        return 1;
                    }
                } else if (safetyNumber.length() == 60) {
                    boolean res;
                    try {
                        res = m.trustIdentityVerifiedSafetyNumber(number, safetyNumber);
                    } catch (InvalidNumberException e) {
                        ErrorUtils.handleInvalidNumberException(e);
                        return 1;
                    }
                    if (!res) {
                        System.err.println(
                                "Failed to set the trust for the safety number of this phone number, make sure the phone number and the safety number are correct.");
                        return 1;
                    }
                } else {
                    System.err.println(
                            "Safety number has invalid format, either specify the old hex fingerprint or the new safety number");
                    return 1;
                }
            } else {
                System.err.println(
                        "You need to specify the fingerprint/safety number you have verified with -v SAFETY_NUMBER");
                return 1;
            }
        }
        return 0;
    }
}
