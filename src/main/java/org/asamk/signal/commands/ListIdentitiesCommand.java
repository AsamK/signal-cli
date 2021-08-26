package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
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

        List<IdentityInfo> identities;
        if (number == null) {
            identities = m.getIdentities();
        } else {
            identities = m.getIdentities(CommandUtil.getSingleRecipientIdentifier(number, m.getUsername()));
        }

        if (outputWriter instanceof PlainTextWriter) {
            final var writer = (PlainTextWriter) outputWriter;
            for (var id : identities) {
                printIdentityFingerprint(writer, m, id);
            }
        } else {
            final var writer = (JsonWriter) outputWriter;
            final var jsonIdentities = identities.stream().map(id -> {
                final var address = m.resolveSignalServiceAddress(id.getRecipientId());
                var safetyNumber = Util.formatSafetyNumber(m.computeSafetyNumber(address, id.getIdentityKey()));
                var scannableSafetyNumber = m.computeSafetyNumberForScanning(address, id.getIdentityKey());
                return new JsonIdentity(address.getNumber().orNull(),
                        address.getUuid().toString(),
                        Hex.toString(id.getFingerprint()),
                        safetyNumber,
                        scannableSafetyNumber == null
                                ? null
                                : Base64.getEncoder().encodeToString(scannableSafetyNumber),
                        id.getTrustLevel().name(),
                        id.getDateAdded().getTime());
            }).collect(Collectors.toList());

            writer.write(jsonIdentities);
        }
    }

    private static final class JsonIdentity {

        public final String number;
        public final String uuid;
        public final String fingerprint;
        public final String safetyNumber;
        public final String scannableSafetyNumber;
        public final String trustLevel;
        public final long addedTimestamp;

        private JsonIdentity(
                final String number,
                final String uuid,
                final String fingerprint,
                final String safetyNumber,
                final String scannableSafetyNumber,
                final String trustLevel,
                final long addedTimestamp
        ) {
            this.number = number;
            this.uuid = uuid;
            this.fingerprint = fingerprint;
            this.safetyNumber = safetyNumber;
            this.scannableSafetyNumber = scannableSafetyNumber;
            this.trustLevel = trustLevel;
            this.addedTimestamp = addedTimestamp;
        }
    }
}
