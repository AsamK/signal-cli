package org.asamk.signal.manager.storage.protocol;

import org.asamk.signal.manager.TrustLevel;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Date;

public class LegacyIdentityInfo {

    SignalServiceAddress address;
    IdentityKey identityKey;
    TrustLevel trustLevel;
    Date added;

    LegacyIdentityInfo(SignalServiceAddress address, IdentityKey identityKey, TrustLevel trustLevel, Date added) {
        this.address = address;
        this.identityKey = identityKey;
        this.trustLevel = trustLevel;
        this.added = added;
    }

    public SignalServiceAddress getAddress() {
        return address;
    }

    public void setAddress(final SignalServiceAddress address) {
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
