package org.asamk.signal.manager.storage.identities;

import org.asamk.signal.manager.TrustLevel;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.whispersystems.libsignal.IdentityKey;

import java.util.Date;

public class IdentityInfo {

    private final RecipientId recipientId;
    private final IdentityKey identityKey;
    private final TrustLevel trustLevel;
    private final Date added;

    IdentityInfo(
            final RecipientId recipientId, IdentityKey identityKey, TrustLevel trustLevel, Date added
    ) {
        this.recipientId = recipientId;
        this.identityKey = identityKey;
        this.trustLevel = trustLevel;
        this.added = added;
    }

    public RecipientId getRecipientId() {
        return recipientId;
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
        return this.added;
    }

    public byte[] getFingerprint() {
        return identityKey.getPublicKey().serialize();
    }
}
