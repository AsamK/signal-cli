package org.asamk.signal.manager.api;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.whispersystems.libsignal.IdentityKey;

import java.util.Date;

public record Identity(
        RecipientAddress recipient,
        IdentityKey identityKey,
        String safetyNumber,
        byte[] scannableSafetyNumber,
        TrustLevel trustLevel,
        Date dateAdded
) {

    public byte[] getFingerprint() {
        return identityKey.getPublicKey().serialize();
    }
}
