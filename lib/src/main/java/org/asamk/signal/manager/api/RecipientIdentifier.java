package org.asamk.signal.manager.api;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;

public sealed interface RecipientIdentifier {

    record NoteToSelf() implements RecipientIdentifier {

        public static NoteToSelf INSTANCE = new NoteToSelf();
    }

    sealed interface Single extends RecipientIdentifier {

        static Single fromString(String identifier, String localNumber) throws InvalidNumberException {
            try {
                if (UuidUtil.isUuid(identifier)) {
                    return new Uuid(UUID.fromString(identifier));
                }

                final var normalizedNumber = PhoneNumberFormatter.formatNumber(identifier, localNumber);
                if (!normalizedNumber.equals(identifier)) {
                    final Logger logger = LoggerFactory.getLogger(RecipientIdentifier.class);
                    logger.debug("Normalized number {} to {}.", identifier, normalizedNumber);
                }
                return new Number(normalizedNumber);
            } catch (org.whispersystems.signalservice.api.util.InvalidNumberException e) {
                throw new InvalidNumberException(e.getMessage(), e);
            }
        }

        static Single fromAddress(RecipientAddress address) {
            if (address.getNumber().isPresent()) {
                return new Number(address.getNumber().get());
            } else if (address.getUuid().isPresent()) {
                return new Uuid(address.getUuid().get());
            }
            throw new AssertionError("RecipientAddress without identifier");
        }

        String getIdentifier();
    }

    record Uuid(UUID uuid) implements Single {

        @Override
        public String getIdentifier() {
            return uuid.toString();
        }
    }

    record Number(String number) implements Single {

        @Override
        public String getIdentifier() {
            return number;
        }
    }

    record Group(GroupId groupId) implements RecipientIdentifier {}
}
