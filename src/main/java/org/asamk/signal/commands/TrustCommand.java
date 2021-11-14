package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.Hex;

import java.util.Base64;
import java.util.Locale;

public class TrustCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "trust";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Set the trust level of a given number.");
        subparser.addArgument("recipient").help("Specify the phone number, for which to set the trust.").required(true);
        var mutTrust = subparser.addMutuallyExclusiveGroup();
        mutTrust.addArgument("-a", "--trust-all-known-keys")
                .help("Trust all known keys of this user, only use this for testing.")
                .action(Arguments.storeTrue());
        mutTrust.addArgument("-v", "--verified-safety-number", "--verified-fingerprint")
                .help("Specify the safety number of the key, only use this option if you have verified the safety number.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        var recipentString = ns.getString("recipient");
        var recipient = CommandUtil.getSingleRecipientIdentifier(recipentString, m.getSelfNumber());
        if (Boolean.TRUE.equals(ns.getBoolean("trust-all-known-keys"))) {
            boolean res = m.trustIdentityAllKeys(recipient);
            if (!res) {
                throw new UserErrorException("Failed to set the trust for this number, make sure the number is correct.");
            }
        } else {
            var safetyNumber = ns.getString("verified-safety-number");
            if (safetyNumber == null) {
                throw new UserErrorException(
                        "You need to specify the fingerprint/safety number you have verified with -v SAFETY_NUMBER");
            }

            safetyNumber = safetyNumber.replaceAll(" ", "");
            if (safetyNumber.length() == 66) {
                byte[] fingerprintBytes;
                try {
                    fingerprintBytes = Hex.toByteArray(safetyNumber.toLowerCase(Locale.ROOT));
                } catch (Exception e) {
                    throw new UserErrorException(
                            "Failed to parse the fingerprint, make sure the fingerprint is a correctly encoded hex string without additional characters.");
                }
                boolean res = m.trustIdentityVerified(recipient, fingerprintBytes);
                if (!res) {
                    throw new UserErrorException(
                            "Failed to set the trust for the fingerprint of this number, make sure the number and the fingerprint are correct.");
                }
            } else if (safetyNumber.length() == 60) {
                boolean res = m.trustIdentityVerifiedSafetyNumber(recipient, safetyNumber);
                if (!res) {
                    throw new UserErrorException(
                            "Failed to set the trust for the safety number of this phone number, make sure the phone number and the safety number are correct.");
                }
            } else {
                final byte[] scannableSafetyNumber;
                try {
                    scannableSafetyNumber = Base64.getDecoder().decode(safetyNumber);
                } catch (IllegalArgumentException e) {
                    throw new UserErrorException(
                            "Safety number has invalid format, either specify the old hex fingerprint or the new safety number");
                }
                boolean res = m.trustIdentityVerifiedSafetyNumber(recipient, scannableSafetyNumber);
                if (!res) {
                    throw new UserErrorException(
                            "Failed to set the trust for the safety number of this phone number, make sure the phone number and the safety number are correct.");
                }
            }
        }
    }
}
