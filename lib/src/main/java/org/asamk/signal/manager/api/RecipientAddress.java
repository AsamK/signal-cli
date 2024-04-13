package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Optional;
import java.util.UUID;

public record RecipientAddress(
        Optional<String> aci, Optional<String> pni, Optional<String> number, Optional<String> username
) {

    public static final UUID UNKNOWN_UUID = UuidUtil.UNKNOWN_UUID;

    /**
     * Construct a RecipientAddress.
     *
     * @param aci    The ACI of the user, if available.
     * @param pni    The PNI of the user, if available.
     * @param number The phone number of the user, if available.
     */
    public RecipientAddress {
        if (aci.isEmpty() && pni.isEmpty() && number.isEmpty() && username.isEmpty()) {
            throw new AssertionError("Must have either a ACI, PNI, username or E164 number!");
        }
    }

    public RecipientAddress(String e164) {
        this(null, null, e164, null);
    }

    public RecipientAddress(UUID uuid) {
        this(uuid.toString(), null, null, null);
    }

    public RecipientAddress(String aci, String pni, String e164, String username) {
        this(Optional.ofNullable(aci),
                Optional.ofNullable(pni),
                Optional.ofNullable(e164),
                Optional.ofNullable(username));
    }

    public Optional<UUID> uuid() {
        return aci.map(UUID::fromString);
    }

    public String getIdentifier() {
        if (aci.isPresent()) {
            return aci.get();
        } else if (number.isPresent()) {
            return number.get();
        } else if (pni.isPresent()) {
            return pni.get();
        } else if (username.isPresent()) {
            return username.get();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public String getLegacyIdentifier() {
        if (number.isPresent()) {
            return number.get();
        } else {
            return getIdentifier();
        }
    }

    public boolean matches(RecipientAddress other) {
        return (aci.isPresent() && other.aci.isPresent() && aci.get().equals(other.aci.get()))
                || (
                pni.isPresent() && other.pni.isPresent() && pni.get().equals(other.pni.get())
        )
                || (number.isPresent() && other.number.isPresent() && number.get().equals(other.number.get()))
                || (username.isPresent() && other.username.isPresent() && username.get().equals(other.username.get()));
    }
}
