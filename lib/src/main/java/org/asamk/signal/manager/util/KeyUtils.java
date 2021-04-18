package org.asamk.signal.manager.util;

import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.signalservice.api.kbs.MasterKey;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class KeyUtils {

    private static final SecureRandom secureRandom = new SecureRandom();

    private KeyUtils() {
    }

    public static IdentityKeyPair getIdentityKeyPair(byte[] publicKeyBytes, byte[] privateKeyBytes) {
        try {
            IdentityKey publicKey = new IdentityKey(publicKeyBytes);
            ECPrivateKey privateKey = Curve.decodePrivatePoint(privateKeyBytes);

            return new IdentityKeyPair(publicKey, privateKey);
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public static IdentityKeyPair generateIdentityKeyPair() {
        var djbKeyPair = Curve.generateKeyPair();
        var djbIdentityKey = new IdentityKey(djbKeyPair.getPublicKey());
        var djbPrivateKey = djbKeyPair.getPrivateKey();

        return new IdentityKeyPair(djbIdentityKey, djbPrivateKey);
    }

    public static List<PreKeyRecord> generatePreKeyRecords(final int offset, final int batchSize) {
        var records = new ArrayList<PreKeyRecord>(batchSize);
        for (var i = 0; i < batchSize; i++) {
            var preKeyId = (offset + i) % Medium.MAX_VALUE;
            var keyPair = Curve.generateKeyPair();
            var record = new PreKeyRecord(preKeyId, keyPair);

            records.add(record);
        }
        return records;
    }

    public static SignedPreKeyRecord generateSignedPreKeyRecord(
            final IdentityKeyPair identityKeyPair, final int signedPreKeyId
    ) {
        var keyPair = Curve.generateKeyPair();
        byte[] signature;
        try {
            signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
        return new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);
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
        return MasterKey.createNew(secureRandom);
    }

    private static String getSecret(int size) {
        var secret = getSecretBytes(size);
        return Base64.getEncoder().encodeToString(secret);
    }

    public static byte[] getSecretBytes(int size) {
        var secret = new byte[size];
        secureRandom.nextBytes(secret);
        return secret;
    }
}
