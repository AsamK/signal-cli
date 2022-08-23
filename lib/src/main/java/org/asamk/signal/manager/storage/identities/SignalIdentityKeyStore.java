package org.asamk.signal.manager.storage.identities;

import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.function.Supplier;

public class SignalIdentityKeyStore implements org.signal.libsignal.protocol.state.IdentityKeyStore {

    private final Supplier<IdentityKeyPair> identityKeyPairSupplier;
    private final int localRegistrationId;
    private final IdentityKeyStore identityKeyStore;

    public SignalIdentityKeyStore(
            final RecipientResolver resolver,
            final Supplier<IdentityKeyPair> identityKeyPairSupplier,
            final int localRegistrationId,
            final IdentityKeyStore identityKeyStore
    ) {
        this.identityKeyPairSupplier = identityKeyPairSupplier;
        this.localRegistrationId = localRegistrationId;
        this.identityKeyStore = identityKeyStore;
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyPairSupplier.get();
    }

    @Override
    public int getLocalRegistrationId() {
        return localRegistrationId;
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        final var serviceId = ServiceId.parseOrThrow(address.getName());

        return identityKeyStore.saveIdentity(serviceId, identityKey);
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        final var serviceId = ServiceId.parseOrThrow(address.getName());

        return identityKeyStore.isTrustedIdentity(serviceId, identityKey, direction);
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        final var serviceId = ServiceId.parseOrThrow(address.getName());
        final var identityInfo = identityKeyStore.getIdentityInfo(serviceId);
        return identityInfo == null ? null : identityInfo.getIdentityKey();
    }
}
