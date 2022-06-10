package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.DateUtils;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class ListIdentitiesCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(ListIdentitiesCommand.class);

    @Override
    public String getName() {
        return "listIdentities";
    }

    private static void printIdentityFingerprint(PlainTextWriter writer, Identity theirId) {
        writer.println("{}: {} Added: {} Fingerprint: {} Safety Number: {}",
                theirId.recipient().getLegacyIdentifier(),
                theirId.trustLevel(),
                DateUtils.formatTimestamp(theirId.dateAddedTimestamp()),
                Hex.toString(theirId.getFingerprint()),
                Util.formatSafetyNumber(theirId.safetyNumber()));
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
                printIdentityFingerprint(writer, id);
            }
        } else {
            final var writer = (JsonWriter) outputWriter;
            final var jsonIdentities = identities.stream().map(id -> {
                final var address = id.recipient();
                var safetyNumber = Util.formatSafetyNumber(id.safetyNumber());
                var scannableSafetyNumber = id.scannableSafetyNumber();
                return new JsonIdentity(address.number().orElse(null),
                        address.uuid().map(UUID::toString).orElse(null),
                        Hex.toString(id.getFingerprint()),
                        safetyNumber,
                        scannableSafetyNumber == null
                                ? null
                                : Base64.getEncoder().encodeToString(scannableSafetyNumber),
                        id.trustLevel().name(),
                        id.dateAddedTimestamp());
            }).toList();

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
