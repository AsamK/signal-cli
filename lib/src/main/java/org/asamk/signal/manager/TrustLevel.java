package org.asamk.signal.manager;

import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;

public enum TrustLevel {
    UNTRUSTED,
    TRUSTED_UNVERIFIED,
    TRUSTED_VERIFIED;

    private static TrustLevel[] cachedValues = null;

    public static TrustLevel fromInt(int i) {
        if (TrustLevel.cachedValues == null) {
            TrustLevel.cachedValues = TrustLevel.values();
        }
        return TrustLevel.cachedValues[i];
    }

    public static TrustLevel fromIdentityState(ContactRecord.IdentityState identityState) {
        switch (identityState) {
            case DEFAULT:
                return TRUSTED_UNVERIFIED;
            case UNVERIFIED:
                return UNTRUSTED;
            case VERIFIED:
                return TRUSTED_VERIFIED;
            case UNRECOGNIZED:
                return null;
        }
        throw new RuntimeException("Unknown identity state: " + identityState);
    }

    public static TrustLevel fromVerifiedState(VerifiedMessage.VerifiedState verifiedState) {
        switch (verifiedState) {
            case DEFAULT:
                return TRUSTED_UNVERIFIED;
            case UNVERIFIED:
                return UNTRUSTED;
            case VERIFIED:
                return TRUSTED_VERIFIED;
        }
        throw new RuntimeException("Unknown verified state: " + verifiedState);
    }

    public VerifiedMessage.VerifiedState toVerifiedState() {
        switch (this) {
            case TRUSTED_UNVERIFIED:
                return VerifiedMessage.VerifiedState.DEFAULT;
            case UNTRUSTED:
                return VerifiedMessage.VerifiedState.UNVERIFIED;
            case TRUSTED_VERIFIED:
                return VerifiedMessage.VerifiedState.VERIFIED;
        }
        throw new RuntimeException("Unknown verified state: " + this);
    }
}
