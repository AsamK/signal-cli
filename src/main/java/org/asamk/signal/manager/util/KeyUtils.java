package org.asamk.signal.manager.util;

import org.asamk.signal.util.RandomUtils;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.util.Base64;

public class KeyUtils {

    private KeyUtils() {
    }

    public static IdentityKeyPair generateIdentityKeyPair() {
        ECKeyPair djbKeyPair = Curve.generateKeyPair();
        IdentityKey djbIdentityKey = new IdentityKey(djbKeyPair.getPublicKey());
        ECPrivateKey djbPrivateKey = djbKeyPair.getPrivateKey();

        return new IdentityKeyPair(djbIdentityKey, djbPrivateKey);
    }

    public static String createSignalingKey() {
        return getSecret(52);
    }

    public static ProfileKey createProfileKey() {
        try {
            return new ProfileKey(getSecretBytes(32));
        } catch (InvalidInputException e) {
            throw new AssertionError("Profile key is guaranteed to be 32 bytes here");
        }
    }

    public static String createPassword() {
        return getSecret(18);
    }

    public static byte[] createStickerUploadKey() {
        return getSecretBytes(32);
    }

    public static MasterKey createMasterKey() {
        return MasterKey.createNew(RandomUtils.getSecureRandom());
    }

    private static String getSecret(int size) {
        byte[] secret = getSecretBytes(size);
        return Base64.encodeBytes(secret);
    }

    public static byte[] getSecretBytes(int size) {
        byte[] secret = new byte[size];
        RandomUtils.getSecureRandom().nextBytes(secret);
        return secret;
    }
}
