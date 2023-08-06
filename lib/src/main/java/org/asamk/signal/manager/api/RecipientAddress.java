package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Optional;
import java.util.UUID;

public record RecipientAddress(Optional<UUID> uuid, Optional<String> number, Optional<String> username) {

    public static final UUID UNKNOWN_UUID = UuidUtil.UNKNOWN_UUID;

    /**
     * Construct a RecipientAddress.
     *
     * @param uuid   The UUID of the user, if available.
     * @param number The phone number of the user, if available.
     */
    public RecipientAddress {
        uuid = uuid.isPresent() && uuid.get().equals(UNKNOWN_UUID) ? Optional.empty() : uuid;
        if (uuid.isEmpty() && number.isEmpty() && username.isEmpty()) {
            throw new AssertionError("Must have either a UUID or E164 number!");
        }
    }

    public RecipientAddress(UUID uuid, String e164) {
        this(Optional.ofNullable(uuid), Optional.ofNullable(e164), Optional.empty());
    }

    public RecipientAddress(UUID uuid, String e164, String username) {
        this(Optional.ofNullable(uuid), Optional.ofNullable(e164), Optional.ofNullable(username));
    }

    public RecipientAddress(SignalServiceAddress address) {
        this(Optional.of(address.getServiceId().getRawUuid()), address.getNumber(), Optional.empty());
    }

    public RecipientAddress(UUID uuid) {
        this(Optional.of(uuid), Optional.empty(), Optional.empty());
    }

    public String getIdentifier() {
        if (uuid.isPresent()) {
            return uuid.get().toString();
        } else if (number.isPresent()) {
            return number.get();
        } else if (username.isPresent()) {
            return username.get();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public String getLegacyIdentifier() {
        if (number.isPresent()) {
            return number.get();
        } else if (uuid.isPresent()) {
            return uuid.get().toString();
        } else if (username.isPresent()) {
            return username.get();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public boolean matches(RecipientAddress other) {
        return (uuid.isPresent() && other.uuid.isPresent() && uuid.get().equals(other.uuid.get()))
                || (number.isPresent() && other.number.isPresent() && number.get().equals(other.number.get()))
                || (username.isPresent() && other.username.isPresent() && username.get().equals(other.username.get()));
    }
}
