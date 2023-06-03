package org.asamk.signal.manager.api;

import org.signal.libsignal.protocol.util.Hex;

import java.util.Base64;
import java.util.Locale;

public sealed interface IdentityVerificationCode {

    record Fingerprint(byte[] fingerprint) implements IdentityVerificationCode {}

    record SafetyNumber(String safetyNumber) implements IdentityVerificationCode {}

    record ScannableSafetyNumber(byte[] safetyNumber) implements IdentityVerificationCode {}

    static IdentityVerificationCode parse(String code) throws Exception {
        code = code.replaceAll(" ", "");
        if (code.length() == 66) {
            final var fingerprintBytes = Hex.fromStringCondensed(code.toLowerCase(Locale.ROOT));
            return new Fingerprint(fingerprintBytes);
        } else if (code.length() == 60) {
            return new SafetyNumber(code);
        } else {
            final var scannableSafetyNumber = Base64.getDecoder().decode(code);
            return new ScannableSafetyNumber(scannableSafetyNumber);
        }
    }
}
