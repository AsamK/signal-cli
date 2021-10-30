package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class ListIdentitiesCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(ListIdentitiesCommand.class);

    @Override
    public String getName() {
        return "listIdentities";
    }

    private static void printIdentityFingerprint(PlainTextWriter writer, Manager m, Identity theirId) {
        final SignalServiceAddress address = theirId.recipient().toSignalServiceAddress();
        var digits = Util.formatSafetyNumber(theirId.safetyNumber());
        writer.println("{}: {} Added: {} Fingerprint: {} Safety Number: {}",
                address.getNumber().orNull(),
                theirId.trustLevel(),
                theirId.dateAdded(),
                Hex.toString(theirId.getFingerprint()),
                digits);
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("List all known identity keys and their trust status, fingerprint and safety number.");
        subparser.addArgument("-n", "--number").help("Only show identity keys for the given phone number.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        var number = ns.getString("number");

        List<Identity> identities;
        if (number == null) {
            identities = m.getIdentities();
        } else {
            identities = m.getIdentities(CommandUtil.getSingleRecipientIdentifier(number, m.getSelfNumber()));
        }

        if (outputWriter instanceof PlainTextWriter writer) {
            for (var id : identities) {
                printIdentityFingerprint(writer, m, id);
            }
        } else {
            final var writer = (JsonWriter) outputWriter;
            final var jsonIdentities = identities.stream().map(id -> {
                final var address = id.recipient().toSignalServiceAddress();
                var safetyNumber = Util.formatSafetyNumber(id.safetyNumber());
                var scannableSafetyNumber = id.scannableSafetyNumber();
                return new JsonIdentity(address.getNumber().orNull(),
                        address.getUuid().toString(),
                        Hex.toString(id.getFingerprint()),
                        safetyNumber,
                        scannableSafetyNumber == null
                                ? null
                                : Base64.getEncoder().encodeToString(scannableSafetyNumber),
                        id.trustLevel().name(),
                        id.dateAdded().getTime());
            }).collect(Collectors.toList());

            writer.write(jsonIdentities);
        }
    }

    private record JsonIdentity(
            String number,
            String uuid,
            String fingerprint,
            String safetyNumber,
            String scannableSafetyNumber,
            String trustLevel,
            long addedTimestamp
    ) {}
}
