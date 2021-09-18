package org.asamk.signal.manager.api;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;

public abstract class RecipientIdentifier {

    public static class NoteToSelf extends RecipientIdentifier {

        public static NoteToSelf INSTANCE = new NoteToSelf();

        private NoteToSelf() {
        }
    }

    public abstract static class Single extends RecipientIdentifier {

        public static Single fromString(String identifier, String localNumber) throws InvalidNumberException {
            return UuidUtil.isUuid(identifier)
                    ? new Uuid(UUID.fromString(identifier))
                    : new Number(PhoneNumberFormatter.formatNumber(identifier, localNumber));
        }

        public static Single fromAddress(SignalServiceAddress address) {
            return new Uuid(address.getUuid());
        }

        public static Single fromAddress(RecipientAddress address) {
            if (address.getNumber().isPresent()) {
                return new Number(address.getNumber().get());
            } else if (address.getUuid().isPresent()) {
                return new Uuid(address.getUuid().get());
            }
            throw new AssertionError("RecipientAddress without identifier");
        }

        public abstract String getIdentifier();
    }

    public static class Uuid extends Single {

        public final UUID uuid;

        public Uuid(final UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Uuid uuid1 = (Uuid) o;

            return uuid.equals(uuid1.uuid);
        }

        @Override
        public int hashCode() {
            return uuid.hashCode();
        }

        @Override
        public String getIdentifier() {
            return uuid.toString();
        }
    }

    public static class Number extends Single {

        public final String number;

        public Number(final String number) {
            this.number = number;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Number number1 = (Number) o;

            return number.equals(number1.number);
        }

        @Override
        public int hashCode() {
            return number.hashCode();
        }

        @Override
        public String getIdentifier() {
            return number;
        }
    }

    public static class Group extends RecipientIdentifier {

        public final GroupId groupId;

        public Group(final GroupId groupId) {
            this.groupId = groupId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Group group = (Group) o;

            return groupId.equals(group.groupId);
        }

        @Override
        public int hashCode() {
            return groupId.hashCode();
        }
    }
}
