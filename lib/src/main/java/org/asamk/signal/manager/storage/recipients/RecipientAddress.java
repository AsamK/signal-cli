package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Optional;

public record RecipientAddress(
        Optional<ACI> aci, Optional<PNI> pni, Optional<String> number, Optional<String> username
) {

    /**
     * Construct a RecipientAddress.
     *
     * @param aci      The ACI of the user, if available.
     * @param pni      The PNI of the user, if available.
     * @param number   The phone number of the user, if available.
     * @param username The username of the user, if available.
     */
    public RecipientAddress {
        if (aci.isPresent() && aci.get().isUnknown()) {
            aci = Optional.empty();
        }
        if (pni.isPresent() && pni.get().isUnknown()) {
            pni = Optional.empty();
        }
        if (aci.isEmpty() && pni.isEmpty() && number.isEmpty() && username.isEmpty()) {
            throw new AssertionError("Must have either a ServiceId, username or E164 number!");
        }
    }

    public RecipientAddress(Optional<ServiceId> serviceId, Optional<String> number) {
        this(serviceId.filter(s -> s instanceof ACI).map(s -> (ACI) s),
                serviceId.filter(s -> s instanceof PNI).map(s -> (PNI) s),
                number,
                Optional.empty());
    }

    public RecipientAddress(ACI aci, String e164) {
        this(Optional.ofNullable(aci), Optional.empty(), Optional.ofNullable(e164), Optional.empty());
    }

    public RecipientAddress(PNI pni, String e164) {
        this(Optional.empty(), Optional.ofNullable(pni), Optional.ofNullable(e164), Optional.empty());
    }

    public RecipientAddress(String e164) {
        this(Optional.empty(), Optional.empty(), Optional.ofNullable(e164), Optional.empty());
    }

    public RecipientAddress(ACI aci, PNI pni, String e164) {
        this(Optional.ofNullable(aci), Optional.ofNullable(pni), Optional.ofNullable(e164), Optional.empty());
    }

    public RecipientAddress(ACI aci, PNI pni, String e164, String username) {
        this(Optional.ofNullable(aci),
                Optional.ofNullable(pni),
                Optional.ofNullable(e164),
                Optional.ofNullable(username));
    }

    public RecipientAddress(SignalServiceAddress address) {
        this(address.getServiceId() instanceof ACI ? Optional.of((ACI) address.getServiceId()) : Optional.empty(),
                address.getServiceId() instanceof PNI ? Optional.of((PNI) address.getServiceId()) : Optional.empty(),
                address.getNumber(),
                Optional.empty());
    }

    public RecipientAddress(org.asamk.signal.manager.api.RecipientAddress address) {
        this(address.aci().map(ACI::parseOrNull),
                address.pni().map(PNI::parseOrNull),
                address.number(),
                address.username());
    }

    public RecipientAddress(ServiceId serviceId) {
        this(Optional.of(serviceId), Optional.empty());
    }

    public RecipientAddress withIdentifiersFrom(RecipientAddress address) {
        return new RecipientAddress(address.aci.or(this::aci),
                address.pni.or(this::pni),
                address.number.or(this::number),
                address.username.or(this::username));
    }

    public RecipientAddress removeIdentifiersFrom(RecipientAddress address) {
        return new RecipientAddress(address.aci.equals(this.aci) ? Optional.empty() : this.aci,
                address.pni.equals(this.pni) ? Optional.empty() : this.pni,
                address.number.equals(this.number) ? Optional.empty() : this.number,
                address.username.equals(this.username) ? Optional.empty() : this.username);
    }

    public Optional<ServiceId> serviceId() {
        return aci.map(aci -> (ServiceId) aci).or(this::pni);
    }

    public String getIdentifier() {
        if (aci.isPresent()) {
            return aci.get().toString();
        } else if (pni.isPresent()) {
            return pni.get().toString();
        } else if (number.isPresent()) {
            return number.get();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public String getLegacyIdentifier() {
        if (number.isPresent()) {
            return number.get();
        } else if (aci.isPresent()) {
            return aci.get().toString();
        } else if (pni.isPresent()) {
            return pni.get().toString();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public boolean matches(RecipientAddress other) {
        return (aci.isPresent() && other.aci.isPresent() && aci.get().equals(other.aci.get())) || (
                pni.isPresent() && other.pni.isPresent() && pni.get().equals(other.pni.get())
        ) || (
                number.isPresent() && other.number.isPresent() && number.get().equals(other.number.get())
        );
    }

    public boolean hasSingleIdentifier() {
        final var identifiersCount = aci().map(s -> 1).orElse(0) + pni().map(s -> 1).orElse(0) + number().map(s -> 1)
                .orElse(0) + username().map(s -> 1).orElse(0);
        return identifiersCount == 1;
    }

    public boolean hasIdentifiersOf(RecipientAddress address) {
        return (address.aci.isEmpty() || address.aci.equals(aci))
                && (address.pni.isEmpty() || address.pni.equals(pni))
                && (address.number.isEmpty() || address.number.equals(number))
                && (address.username.isEmpty() || address.username.equals(username));
    }

    public boolean hasAdditionalIdentifiersThan(RecipientAddress address) {
        return (
                aci.isPresent() && (
                        address.aci.isEmpty() || !address.aci.equals(aci)

                )
        ) || (
                pni.isPresent() && (
                        address.pni.isEmpty() || !address.pni.equals(pni)
                )
        ) || (
                number.isPresent() && (
                        address.number.isEmpty() || !address.number.equals(number)
                )
        ) || (
                username.isPresent() && (
                        address.username.isEmpty() || !address.username.equals(username)
                )
        );
    }

    public boolean hasOnlyPniAndNumber() {
        return pni.isPresent() && aci.isEmpty() && number.isPresent();
    }

    public SignalServiceAddress toSignalServiceAddress() {
        return new SignalServiceAddress(serviceId().orElse(ACI.UNKNOWN), number);
    }

    public org.asamk.signal.manager.api.RecipientAddress toApiRecipientAddress() {
        return new org.asamk.signal.manager.api.RecipientAddress(aci().map(ServiceId::toString),
                pni().map(ServiceId::toString),
                number(),
                username());
    }
}
