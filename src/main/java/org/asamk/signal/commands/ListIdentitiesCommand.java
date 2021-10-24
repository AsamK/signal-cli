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
        final SignalServiceAddress address = theirId.getRecipient().toSignalServiceAddress();
        var digits = Util.formatSafetyNumber(theirId.getSafetyNumber());
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
                final var address = id.getRecipient().toSignalServiceAddress();
                var safetyNumber = Util.formatSafetyNumber(id.getSafetyNumber());
                var scannableSafetyNumber = id.getScannableSafetyNumber();
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
