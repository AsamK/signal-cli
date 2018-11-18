package org.asamk.signal.manager;

import org.whispersystems.signalservice.internal.util.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

class KeyUtils {

    private KeyUtils() {
    }

    static String createSignalingKey() {
        return getSecret(52);
    }

    static byte[] createProfileKey() {
        return getSecretBytes(32);
    }

    static String createPassword() {
        return getSecret(18);
    }

    static byte[] createGroupId() {
        return getSecretBytes(16);
    }

    private static String getSecret(int size) {
        byte[] secret = getSecretBytes(size);
        return Base64.encodeBytes(secret);
    }

    private static byte[] getSecretBytes(int size) {
        byte[] secret = new byte[size];
        getSecureRandom().nextBytes(secret);
        return secret;
    }

    private static SecureRandom getSecureRandom() {
        try {
            return SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
