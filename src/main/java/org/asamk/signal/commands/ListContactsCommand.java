package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.CommandUtil;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public class ListContactsCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "listContacts";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Show a list of known contacts with names and profiles.");
        subparser.addArgument("recipient").help("Specify one ore more phone numbers to show.").nargs("*");
        subparser.addArgument("-a", "--all-recipients")
                .action(Arguments.storeTrue())
                .help("Include all known recipients, not only contacts.");
        subparser.addArgument("--blocked")
                .type(Boolean.class)
                .help("Specify if only blocked or unblocked contacts should be shown (default: all contacts)");
        subparser.addArgument("--name").help("Find contacts with the given contact or profile name.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var allRecipients = Boolean.TRUE.equals(ns.getBoolean("all-recipients"));
        final var blocked = ns.getBoolean("blocked");
        final var recipientStrings = ns.<String>getList("recipient");
        final var recipientIdentifiers = CommandUtil.getSingleRecipientIdentifiers(recipientStrings, m.getSelfNumber());
        final var name = ns.getString("name");
        final var recipients = m.getRecipients(!allRecipients,
                Optional.ofNullable(blocked),
                recipientIdentifiers,
                Optional.ofNullable(name));

        if (outputWriter instanceof PlainTextWriter writer) {
            for (var r : recipients) {
                final var contact = r.getContact() == null ? Contact.newBuilder().build() : r.getContact();
                final var profile = r.getProfile() == null ? Profile.newBuilder().build() : r.getProfile();
                writer.println("Number: {} Name: {} Profile name: {} Color: {} Blocked: {} Message expiration: {}",
                        r.getAddress().getLegacyIdentifier(),
                        contact.getName(),
                        profile.getDisplayName(),
                        contact.getColor(),
                        contact.isBlocked(),
                        contact.getMessageExpirationTime() == 0
                                ? "disabled"
                                : contact.getMessageExpirationTime() + "s");
            }
        } else {
            final var writer = (JsonWriter) outputWriter;
            final var jsonContacts = recipients.stream().map(r -> {
                final var address = r.getAddress();
                final var contact = r.getContact() == null ? Contact.newBuilder().build() : r.getContact();
                return new JsonContact(address.number().orElse(null),
                        address.uuid().map(UUID::toString).orElse(null),
                        contact.getName(),
                        contact.getColor(),
                        contact.isBlocked(),
                        contact.getMessageExpirationTime(),
                        r.getProfile() == null
                                ? null
                                : new JsonContact.JsonProfile(r.getProfile().getLastUpdateTimestamp(),
                                        r.getProfile().getGivenName(),
                                        r.getProfile().getFamilyName(),
                                        r.getProfile().getAbout(),
                                        r.getProfile().getAboutEmoji(),
                                        r.getProfile().getMobileCoinAddress() == null
                                                ? null
                                                : Base64.getEncoder()
                                                        .encodeToString(r.getProfile().getMobileCoinAddress())));
            }).toList();

            writer.write(jsonContacts);
        }
    }

    private record JsonContact(
            String number,
            String uuid,
            String name,
            String color,
            boolean isBlocked,
            int messageExpirationTime,
            JsonProfile profile
    ) {

        private record JsonProfile(
                long lastUpdateTimestamp,
                String givenName,
                String familyName,
                String about,
                String aboutEmoji,
                String mobileCoinAddress
        ) {}
    }
}
