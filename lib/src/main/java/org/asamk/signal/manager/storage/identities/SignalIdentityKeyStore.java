package org.asamk.signal.manager.storage.identities;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.SignalProtocolAddress;

import java.util.function.Supplier;

public class SignalIdentityKeyStore implements org.signal.libsignal.protocol.state.IdentityKeyStore {

    private final RecipientResolver resolver;
    private final Supplier<IdentityKeyPair> identityKeyPairSupplier;
    private final int localRegistrationId;
    private final IdentityKeyStore identityKeyStore;

    public SignalIdentityKeyStore(
            final RecipientResolver resolver,
            final Supplier<IdentityKeyPair> identityKeyPairSupplier,
            final int localRegistrationId,
            final IdentityKeyStore identityKeyStore
    ) {
        this.resolver = resolver;
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
        final var recipientId = resolveRecipient(address.getName());

        return identityKeyStore.saveIdentity(recipientId, identityKey);
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        var recipientId = resolveRecipient(address.getName());

        return identityKeyStore.isTrustedIdentity(recipientId, identityKey, direction);
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        var recipientId = resolveRecipient(address.getName());
        final var identityInfo = identityKeyStore.getIdentityInfo(recipientId);
        return identityInfo == null ? null : identityInfo.getIdentityKey();
    }

    /**
     * @param identifier can be either a serialized uuid or an e164 phone number
     */
    private RecipientId resolveRecipient(String identifier) {
        return resolver.resolveRecipient(identifier);
    }
}
