package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.IdentityVerificationCode;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;

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
            try {
                final var res = m.trustIdentityAllKeys(recipient);
                if (!res) {
                    throw new UserErrorException(
                            "Failed to set the trust for this number, make sure the number is correct.");
                }
            } catch (UnregisteredRecipientException e) {
                throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
            }
        } else {
            var safetyNumber = ns.getString("verified-safety-number");
            if (safetyNumber == null) {
                throw new UserErrorException(
                        "You need to specify the fingerprint/safety number you have verified with -v SAFETY_NUMBER");
            }

            final IdentityVerificationCode verificationCode;
            try {
                verificationCode = IdentityVerificationCode.parse(safetyNumber);
            } catch (Exception e) {
                throw new UserErrorException(
                        "Safety number has invalid format, either specify the old hex fingerprint or the new safety number");
            }

            try {
                final var res = m.trustIdentityVerified(recipient, verificationCode);
                if (!res) {
                    throw new UserErrorException(
                            "Failed to set the trust for this number, make sure the number and the fingerprint/safety number are correct.");
                }
            } catch (UnregisteredRecipientException e) {
                throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
            }
        }
    }
}
