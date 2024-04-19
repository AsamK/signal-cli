package org.asamk.signal.manager.api;

public record Identity(
        RecipientAddress recipient,
        byte[] fingerprint,
        String safetyNumber,
        byte[] scannableSafetyNumber,
        TrustLevel trustLevel,
        long dateAddedTimestamp
) {}
