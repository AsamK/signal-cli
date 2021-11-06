package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Optional;
import java.util.UUID;

public class RecipientAddress {

    public static final UUID UNKNOWN_UUID = ACI.UNKNOWN.uuid();

    private final Optional<UUID> uuid;
    private final Optional<String> e164;

    /**
     * Construct a RecipientAddress.
     *
     * @param uuid The UUID of the user, if available.
     * @param e164 The phone number of the user, if available.
     */
    public RecipientAddress(Optional<UUID> uuid, Optional<String> e164) {
        uuid = uuid.isPresent() && uuid.get().equals(UNKNOWN_UUID) ? Optional.empty() : uuid;
        if (uuid.isEmpty() && e164.isEmpty()) {
            throw new AssertionError("Must have either a UUID or E164 number!");
        }

        this.uuid = uuid;
        this.e164 = e164;
    }

    public RecipientAddress(UUID uuid, String e164) {
        this(Optional.ofNullable(uuid), Optional.ofNullable(e164));
    }

    public RecipientAddress(SignalServiceAddress address) {
        this(Optional.of(address.getAci().uuid()), Optional.ofNullable(address.getNumber().orNull()));
    }

    public RecipientAddress(UUID uuid) {
        this(Optional.of(uuid), Optional.empty());
    }

    public Optional<String> getNumber() {
        return e164;
    }

    public Optional<UUID> getUuid() {
        return uuid;
    }

    public String getIdentifier() {
        if (uuid.isPresent()) {
            return uuid.get().toString();
        } else if (e164.isPresent()) {
            return e164.get();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public String getLegacyIdentifier() {
        if (e164.isPresent()) {
            return e164.get();
        } else if (uuid.isPresent()) {
            return uuid.get().toString();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public boolean matches(RecipientAddress other) {
        return (uuid.isPresent() && other.uuid.isPresent() && uuid.get().equals(other.uuid.get())) || (
                e164.isPresent() && other.e164.isPresent() && e164.get().equals(other.e164.get())
        );
    }

    public SignalServiceAddress toSignalServiceAddress() {
        return new SignalServiceAddress(ACI.from(uuid.orElse(UNKNOWN_UUID)),
                org.whispersystems.libsignal.util.guava.Optional.fromNullable(e164.orElse(null)));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final RecipientAddress that = (RecipientAddress) o;

        if (!uuid.equals(that.uuid)) return false;
        return e164.equals(that.e164);
    }

    @Override
    public int hashCode() {
        int result = uuid.hashCode();
        result = 31 * result + e164.hashCode();
        return result;
    }
}
