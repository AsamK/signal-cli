package org.asamk.signal.manager;

import org.asamk.signal.util.RandomUtils;
import org.whispersystems.signalservice.internal.util.Base64;

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
        RandomUtils.getSecureRandom().nextBytes(secret);
        return secret;
    }
}
