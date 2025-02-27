package org.asamk.signal.manager.util;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.internal.push.PaymentAddress;

import okio.ByteString;

public class PaymentUtils {

    private static final Logger logger = LoggerFactory.getLogger(PaymentUtils.class);

    private PaymentUtils() {
    }

    /**
     * Signs the supplied address bytes with the {@link IdentityKeyPair}'s private key and returns a proto that includes it, and it's signature.
     */
    public static PaymentAddress signPaymentsAddress(byte[] publicAddressBytes, ECPrivateKey privateKey) {
        byte[] signature = privateKey.calculateSignature(publicAddressBytes);

        return new PaymentAddress.Builder().mobileCoin(new PaymentAddress.MobileCoin.Builder().publicAddress(ByteString.of(
                publicAddressBytes)).signature(ByteString.of(signature)).build()).build();
    }

    /**
     * Verifies that the payments address is signed with the supplied {@link IdentityKey}.
     * <p>
     * Returns the validated bytes if so, otherwise returns null.
     */
    public static byte[] verifyPaymentsAddress(PaymentAddress paymentAddress, ECPublicKey publicKey) {
        final var mobileCoinAddress = paymentAddress.mobileCoin;
        if (mobileCoinAddress == null
                || mobileCoinAddress.publicAddress == null
                || mobileCoinAddress.signature == null) {
            logger.debug("Got payment address without mobile coin address, ignoring.");
            return null;
        }

        byte[] bytes = mobileCoinAddress.publicAddress.toByteArray();
        byte[] signature = mobileCoinAddress.signature.toByteArray();

        if (signature.length != 64 || !publicKey.verifySignature(bytes, signature)) {
            logger.debug("Got mobile coin address with invalid signature, ignoring.");
            return null;
        }

        return bytes;
    }
}
