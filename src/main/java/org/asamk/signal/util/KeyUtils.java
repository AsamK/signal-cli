package org.asamk.signal.util;

import org.whispersystems.signalservice.internal.util.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class KeyUtils {

    private KeyUtils() {
    }

    public static String createSignalingKey() {
        return getSecret(52);
    }

    public static byte[] createProfileKey() {
        return getSecretBytes(32);
    }

    public static String createPassword() {
        return getSecret(18);
    }

    public static byte[] createGroupId() {
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
