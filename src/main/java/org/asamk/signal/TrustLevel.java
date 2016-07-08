package org.asamk.signal;

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
}
