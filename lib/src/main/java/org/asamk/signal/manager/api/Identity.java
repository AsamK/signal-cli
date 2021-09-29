package org.asamk.signal.manager.api;

import org.asamk.signal.manager.TrustLevel;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.whispersystems.libsignal.IdentityKey;

import java.util.Date;

public class Identity {

    private final RecipientAddress recipient;
    private final IdentityKey identityKey;
    private final String safetyNumber;
    private final byte[] scannableSafetyNumber;
    private final TrustLevel trustLevel;
    private final Date dateAdded;

    public Identity(
            final RecipientAddress recipient,
            final IdentityKey identityKey,
            final String safetyNumber,
            final byte[] scannableSafetyNumber,
            final TrustLevel trustLevel,
            final Date dateAdded
    ) {
        this.recipient = recipient;
        this.identityKey = identityKey;
        this.safetyNumber = safetyNumber;
        this.scannableSafetyNumber = scannableSafetyNumber;
        this.trustLevel = trustLevel;
        this.dateAdded = dateAdded;
    }

    public RecipientAddress getRecipient() {
        return recipient;
    }

    public IdentityKey getIdentityKey() {
        return this.identityKey;
    }

    public TrustLevel getTrustLevel() {
        return this.trustLevel;
    }

    boolean isTrusted() {
        return trustLevel == TrustLevel.TRUSTED_UNVERIFIED || trustLevel == TrustLevel.TRUSTED_VERIFIED;
    }

    public Date getDateAdded() {
        return this.dateAdded;
    }

    public byte[] getFingerprint() {
        return identityKey.getPublicKey().serialize();
    }

    public String getSafetyNumber() {
        return safetyNumber;
    }

    public byte[] getScannableSafetyNumber() {
        return scannableSafetyNumber;
    }
}
