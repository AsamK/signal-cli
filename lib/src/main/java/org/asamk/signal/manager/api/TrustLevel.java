package org.asamk.signal.manager.api;

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
        return switch (identityState) {
            case DEFAULT -> TRUSTED_UNVERIFIED;
            case UNVERIFIED -> UNTRUSTED;
            case VERIFIED -> TRUSTED_VERIFIED;
        };
    }

    public static TrustLevel fromVerifiedState(VerifiedMessage.VerifiedState verifiedState) {
        return switch (verifiedState) {
            case DEFAULT -> TRUSTED_UNVERIFIED;
            case UNVERIFIED -> UNTRUSTED;
            case VERIFIED -> TRUSTED_VERIFIED;
        };
    }

    public VerifiedMessage.VerifiedState toVerifiedState() {
        return switch (this) {
            case TRUSTED_UNVERIFIED -> VerifiedMessage.VerifiedState.DEFAULT;
            case UNTRUSTED -> VerifiedMessage.VerifiedState.UNVERIFIED;
            case TRUSTED_VERIFIED -> VerifiedMessage.VerifiedState.VERIFIED;
        };
    }

    public boolean isTrusted() {
        return switch (this) {
            case TRUSTED_UNVERIFIED, TRUSTED_VERIFIED -> true;
            case UNTRUSTED -> false;
        };
    }
}
