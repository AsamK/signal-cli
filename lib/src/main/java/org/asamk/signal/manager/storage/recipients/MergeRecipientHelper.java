package org.asamk.signal.manager.storage.recipients;

import org.asamk.signal.manager.api.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MergeRecipientHelper {

    private final static Logger logger = LoggerFactory.getLogger(MergeRecipientHelper.class);

    static Pair<RecipientId, List<RecipientId>> resolveRecipientTrustedLocked(
            Store store, RecipientAddress address
    ) throws SQLException {
        // address has serviceId and number, optionally also pni

        final var recipients = store.findAllByAddress(address);

        if (recipients.isEmpty()) {
            logger.debug("Got new recipient, serviceId, PNI and number are unknown");
            return new Pair<>(store.addNewRecipient(address), List.of());
        }

        if (recipients.size() == 1) {
            final var recipient = recipients.stream().findFirst().get();
            if (recipient.address().hasIdentifiersOf(address)) {
                return new Pair<>(recipient.id(), List.of());
            }

            if (recipient.address().serviceId().isEmpty() || (
                    recipient.address().serviceId().equals(address.serviceId())
            ) || (
                    recipient.address().pni().isPresent() && recipient.address().pni().equals(address.serviceId())
            ) || (
                    recipient.address().serviceId().equals(address.pni())
            ) || (
                    address.pni().isPresent() && address.pni().equals(recipient.address().pni())
            )) {
                logger.debug("Got existing recipient {}, updating with high trust address", recipient.id());
                store.updateRecipientAddress(recipient.id(), recipient.address().withIdentifiersFrom(address));
                return new Pair<>(recipient.id(), List.of());
            }

            logger.debug(
                    "Got recipient {} existing with number/pni, but different serviceId, so stripping its number and adding new recipient",
                    recipient.id());
            store.updateRecipientAddress(recipient.id(), recipient.address().removeIdentifiersFrom(address));

            return new Pair<>(store.addNewRecipient(address), List.of());
        }

        var resultingRecipient = recipients.stream()
                .filter(r -> r.address().serviceId().equals(address.serviceId()) || r.address()
                        .pni()
                        .equals(address.serviceId()))
                .findFirst();
        if (resultingRecipient.isEmpty() && address.pni().isPresent()) {
            resultingRecipient = recipients.stream().filter(r -> r.address().serviceId().equals(address.pni()) || (
                    address.serviceId().equals(address.pni()) && r.address().pni().equals(address.pni())
            )).findFirst();
        }

        final Set<RecipientWithAddress> remainingRecipients;
        if (resultingRecipient.isEmpty()) {
            remainingRecipients = recipients;
        } else {
            remainingRecipients = new HashSet<>(recipients);
            remainingRecipients.remove(resultingRecipient.get());
        }

        final var recipientsToBeMerged = new HashSet<RecipientWithAddress>();
        final var recipientsToBeStripped = new HashSet<RecipientWithAddress>();
        for (final var recipient : remainingRecipients) {
            if (!recipient.address().hasAdditionalIdentifiersThan(address)) {
                recipientsToBeMerged.add(recipient);
                continue;
            }

            if (recipient.address().hasOnlyPniAndNumber()) {
                // PNI and phone number are linked by the server
                recipientsToBeMerged.add(recipient);
                continue;
            }

            recipientsToBeStripped.add(recipient);
        }

        logger.debug("Got separate recipients for high trust identifiers {}, need to merge ({}) and strip ({})",
                address,
                recipientsToBeMerged.stream().map(r -> r.id().toString()).collect(Collectors.joining(", ")),
                recipientsToBeStripped.stream().map(r -> r.id().toString()).collect(Collectors.joining(", ")));

        RecipientAddress finalAddress = resultingRecipient.map(RecipientWithAddress::address).orElse(null);
        for (final var recipient : recipientsToBeMerged) {
            if (finalAddress == null) {
                finalAddress = recipient.address();
            } else {
                finalAddress = finalAddress.withIdentifiersFrom(recipient.address());
            }
            store.removeRecipientAddress(recipient.id());
        }
        if (finalAddress == null) {
            finalAddress = address;
        } else {
            finalAddress = finalAddress.withIdentifiersFrom(address);
        }

        for (final var recipient : recipientsToBeStripped) {
            store.updateRecipientAddress(recipient.id(), recipient.address().removeIdentifiersFrom(address));
        }

        // Create fixed RecipientIds that won't update its id after merged
        final var toBeMergedRecipientIds = recipientsToBeMerged.stream()
                .map(r -> new RecipientId(r.id().id(), null))
                .toList();

        if (resultingRecipient.isPresent()) {
            store.updateRecipientAddress(resultingRecipient.get().id(), finalAddress);
            return new Pair<>(resultingRecipient.get().id(), toBeMergedRecipientIds);
        }

        return new Pair<>(store.addNewRecipient(finalAddress), toBeMergedRecipientIds);
    }

    public interface Store {

        Set<RecipientWithAddress> findAllByAddress(final RecipientAddress address) throws SQLException;

        RecipientId addNewRecipient(final RecipientAddress address) throws SQLException;

        void updateRecipientAddress(RecipientId recipientId, final RecipientAddress address) throws SQLException;

        void removeRecipientAddress(RecipientId recipientId) throws SQLException;
    }
}
