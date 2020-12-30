package org.asamk.signal.manager;

import org.asamk.signal.util.RandomUtils;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.util.Base64;

class KeyUtils {

    private KeyUtils() {
    }

    static String createSignalingKey() {
        return getSecret(52);
    }

    static ProfileKey createProfileKey() {
        try {
            return new ProfileKey(getSecretBytes(32));
        } catch (InvalidInputException e) {
            throw new AssertionError("Profile key is guaranteed to be 32 bytes here");
        }
    }

    static String createPassword() {
        return getSecret(18);
    }

    static byte[] createStickerUploadKey() {
        return getSecretBytes(32);
    }

    private static String getSecret(int size) {
        byte[] secret = getSecretBytes(size);
        return Base64.encodeBytes(secret);
    }

    static byte[] getSecretBytes(int size) {
        byte[] secret = new byte[size];
        RandomUtils.getSecureRandom().nextBytes(secret);
        return secret;
    }
}
