package org.asamk.signal.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class RandomUtils {

    private static final ThreadLocal<SecureRandom> LOCAL_RANDOM = ThreadLocal.withInitial(() -> {
        var rand = getSecureRandomUnseeded();

        // Let the SecureRandom seed it self initially
        rand.nextBoolean();

        return rand;
    });

    private static SecureRandom getSecureRandomUnseeded() {
        try {
            return SecureRandom.getInstance("NativePRNG");
        } catch (NoSuchAlgorithmException e) {
            // Fallback to SHA1PRNG if NativePRNG is not available (e.g. on windows)
            try {
                return SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException e1) {
                // Fallback to default
                return new SecureRandom();
            }
        }
    }

    public static SecureRandom getSecureRandom() {
        return LOCAL_RANDOM.get();
    }
}
