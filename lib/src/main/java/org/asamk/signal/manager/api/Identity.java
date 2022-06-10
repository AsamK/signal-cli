package org.asamk.signal.manager.api;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.signal.libsignal.protocol.IdentityKey;

public record Identity(
        RecipientAddress recipient,
        IdentityKey identityKey,
        String safetyNumber,
        byte[] scannableSafetyNumber,
        TrustLevel trustLevel,
        long dateAddedTimestamp
) {

    public byte[] getFingerprint() {
        return identityKey.getPublicKey().serialize();
    }
}
