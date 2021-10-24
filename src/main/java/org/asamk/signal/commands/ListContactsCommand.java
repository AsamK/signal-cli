package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.manager.Manager;

import java.util.UUID;
import java.util.stream.Collectors;

public class ListContactsCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "listContacts";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Show a list of known contacts with names.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m, final OutputWriter outputWriter) {
        var contacts = m.getContacts();

        if (outputWriter instanceof PlainTextWriter writer) {
            for (var c : contacts) {
                final var contact = c.second();
                writer.println("Number: {} Name: {} Blocked: {} Message expiration: {}",
                        c.first().getLegacyIdentifier(),
                        contact.getName(),
                        contact.isBlocked(),
                        contact.getMessageExpirationTime() == 0
                                ? "disabled"
                                : contact.getMessageExpirationTime() + "s");
            }
        } else {
            final var writer = (JsonWriter) outputWriter;
            final var jsonContacts = contacts.stream().map(contactPair -> {
                final var address = contactPair.first();
                final var contact = contactPair.second();
                return new JsonContact(address.getNumber().orElse(null),
                        address.getUuid().map(UUID::toString).orElse(null),
                        contact.getName(),
                        contact.isBlocked(),
                        contact.getMessageExpirationTime());
            }).collect(Collectors.toList());

            writer.write(jsonContacts);
        }
    }

    private record JsonContact(String number, String uuid, String name, boolean isBlocked, int messageExpirationTime) {}
}
