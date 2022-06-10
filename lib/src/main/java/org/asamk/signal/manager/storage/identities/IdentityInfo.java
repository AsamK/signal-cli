package org.asamk.signal.manager.storage.identities;

import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.libsignal.protocol.IdentityKey;

public class IdentityInfo {

    private final RecipientId recipientId;
    private final IdentityKey identityKey;
    private final TrustLevel trustLevel;
    private final long addedTimestamp;

    IdentityInfo(
            final RecipientId recipientId, IdentityKey identityKey, TrustLevel trustLevel, long addedTimestamp
    ) {
        this.recipientId = recipientId;
        this.identityKey = identityKey;
        this.trustLevel = trustLevel;
        this.addedTimestamp = addedTimestamp;
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

    public long getDateAddedTimestamp() {
        return this.addedTimestamp;
    }
}
