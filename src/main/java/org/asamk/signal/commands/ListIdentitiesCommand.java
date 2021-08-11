package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.List;

public class ListIdentitiesCommand implements LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(ListIdentitiesCommand.class);
    private final OutputWriter outputWriter;

    public ListIdentitiesCommand(final OutputWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    private static void printIdentityFingerprint(PlainTextWriter writer, Manager m, IdentityInfo theirId) {
        final SignalServiceAddress address = m.resolveSignalServiceAddress(theirId.getRecipientId());
        var digits = Util.formatSafetyNumber(m.computeSafetyNumber(address, theirId.getIdentityKey()));
        writer.println("{}: {} Added: {} Fingerprint: {} Safety Number: {}",
                address.getNumber().orNull(),
                theirId.getTrustLevel(),
                theirId.getDateAdded(),
                Hex.toString(theirId.getFingerprint()),
                digits);
    }

    public static void attachToSubparser(final Subparser subparser) {
        subparser.help("List all known identity keys and their trust status, fingerprint and safety number.");
        subparser.addArgument("-n", "--number").help("Only show identity keys for the given phone number.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        final var writer = (PlainTextWriter) outputWriter;

        var number = ns.getString("number");

        if (number == null) {
            for (var identity : m.getIdentities()) {
                printIdentityFingerprint(writer, m, identity);
            }
            return;
        }

        List<IdentityInfo> identities;
        try {
            identities = m.getIdentities(number);
        } catch (InvalidNumberException e) {
            throw new UserErrorException("Invalid number: " + e.getMessage());
        }

        for (var id : identities) {
            printIdentityFingerprint(writer, m, id);
        }
    }
}
