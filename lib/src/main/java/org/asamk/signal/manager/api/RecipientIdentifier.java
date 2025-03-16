package org.asamk.signal.manager.api;

import org.asamk.signal.manager.util.PhoneNumberFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;

public sealed interface RecipientIdentifier {

    String getIdentifier();

    record NoteToSelf() implements RecipientIdentifier {

        public static final NoteToSelf INSTANCE = new NoteToSelf();

        @Override
        public String getIdentifier() {
            return "Note-To-Self";
        }
    }

    sealed interface Single extends RecipientIdentifier {

        static Single fromString(String identifier, String localNumber) throws InvalidNumberException {
            if (UuidUtil.isUuid(identifier)) {
                return new Uuid(UUID.fromString(identifier));
            }

            if (identifier.startsWith("PNI:")) {
                final var pni = identifier.substring(4);
                if (!UuidUtil.isUuid(pni)) {
                    throw new InvalidNumberException("Invalid PNI");
                }
                return new Pni(UUID.fromString(pni));
            }

            if (identifier.startsWith("u:")) {
                return new Username(identifier.substring(2));
            }

            final var normalizedNumber = PhoneNumberFormatter.formatNumber(identifier, localNumber);
            if (!normalizedNumber.equals(identifier)) {
                final Logger logger = LoggerFactory.getLogger(RecipientIdentifier.class);
                logger.debug("Normalized number {} to {}.", identifier, normalizedNumber);
            }
            return new Number(normalizedNumber);
        }

        static Single fromAddress(RecipientAddress address) {
            if (address.number().isPresent()) {
                return new Number(address.number().get());
            } else if (address.aci().isPresent()) {
                return new Uuid(UUID.fromString(address.aci().get()));
            } else if (address.pni().isPresent()) {
                return new Pni(UUID.fromString(address.pni().get().substring(4)));
            } else if (address.username().isPresent()) {
                return new Username(address.username().get());
            }
            throw new AssertionError("RecipientAddress without identifier");
        }

        RecipientAddress toPartialRecipientAddress();
    }

    record Uuid(UUID uuid) implements Single {

        @Override
        public String getIdentifier() {
            return uuid.toString();
        }

        @Override
        public RecipientAddress toPartialRecipientAddress() {
            return new RecipientAddress(uuid);
        }
    }

    record Pni(UUID pni) implements Single {

        @Override
        public String getIdentifier() {
            return "PNI:" + pni.toString();
        }

        @Override
        public RecipientAddress toPartialRecipientAddress() {
            return new RecipientAddress(null, getIdentifier(), null, null);
        }
    }

    record Number(String number) implements Single {

        @Override
        public String getIdentifier() {
            return number;
        }

        @Override
        public RecipientAddress toPartialRecipientAddress() {
            return new RecipientAddress(number);
        }
    }

    record Username(String username) implements Single {

        @Override
        public String getIdentifier() {
            return "u:" + username;
        }

        @Override
        public RecipientAddress toPartialRecipientAddress() {
            return new RecipientAddress(null, null, null, username);
        }
    }

    record Group(GroupId groupId) implements RecipientIdentifier {

        @Override
        public String getIdentifier() {
            return groupId.toBase64();
        }
    }
}
