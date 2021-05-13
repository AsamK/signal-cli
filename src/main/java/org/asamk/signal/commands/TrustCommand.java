package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
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
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        var number = ns.getString("number");
        if (ns.getBoolean("trust-all-known-keys")) {
            boolean res;
            try {
                res = m.trustIdentityAllKeys(number);
            } catch (InvalidNumberException e) {
                throw new UserErrorException("Failed to parse recipient: " + e.getMessage());
            }
            if (!res) {
                throw new UserErrorException("Failed to set the trust for this number, make sure the number is correct.");
            }
        } else {
            var safetyNumber = ns.getString("verified-safety-number");
            if (safetyNumber != null) {
                safetyNumber = safetyNumber.replaceAll(" ", "");
                if (safetyNumber.length() == 66) {
                    byte[] fingerprintBytes;
                    try {
                        fingerprintBytes = Hex.toByteArray(safetyNumber.toLowerCase(Locale.ROOT));
                    } catch (Exception e) {
                        throw new UserErrorException(
                                "Failed to parse the fingerprint, make sure the fingerprint is a correctly encoded hex string without additional characters.");
                    }
                    boolean res;
                    try {
                        res = m.trustIdentityVerified(number, fingerprintBytes);
                    } catch (InvalidNumberException e) {
                        throw new UserErrorException("Failed to parse recipient: " + e.getMessage());
                    }
                    if (!res) {
                        throw new UserErrorException(
                                "Failed to set the trust for the fingerprint of this number, make sure the number and the fingerprint are correct.");
                    }
                } else if (safetyNumber.length() == 60) {
                    boolean res;
                    try {
                        res = m.trustIdentityVerifiedSafetyNumber(number, safetyNumber);
                    } catch (InvalidNumberException e) {
                        throw new UserErrorException("Failed to parse recipient: " + e.getMessage());
                    }
                    if (!res) {
                        throw new UserErrorException(
                                "Failed to set the trust for the safety number of this phone number, make sure the phone number and the safety number are correct.");
                    }
                } else {
                    throw new UserErrorException(
                            "Safety number has invalid format, either specify the old hex fingerprint or the new safety number");
                }
            } else {
                throw new UserErrorException(
                        "You need to specify the fingerprint/safety number you have verified with -v SAFETY_NUMBER");
            }
        }
    }
}
