package org.asamk.signal.manager.storage.protocol;

import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.signal.libsignal.protocol.IdentityKey;

import java.util.Date;

public class LegacyIdentityInfo {

    RecipientAddress address;
    final IdentityKey identityKey;
    final TrustLevel trustLevel;
    final Date added;

    LegacyIdentityInfo(RecipientAddress address, IdentityKey identityKey, TrustLevel trustLevel, Date added) {
        this.address = address;
        this.identityKey = identityKey;
        this.trustLevel = trustLevel;
        this.added = added;
    }

    public RecipientAddress getAddress() {
        return address;
    }

    public void setAddress(final RecipientAddress address) {
        this.address = address;
    }

    boolean isTrusted() {
        return trustLevel == TrustLevel.TRUSTED_UNVERIFIED || trustLevel == TrustLevel.TRUSTED_VERIFIED;
    }

    public IdentityKey getIdentityKey() {
        return this.identityKey;
    }

    public TrustLevel getTrustLevel() {
        return this.trustLevel;
    }

    public Date getDateAdded() {
        return this.added;
    }

    public byte[] getFingerprint() {
        return identityKey.getPublicKey().serialize();
    }
}
