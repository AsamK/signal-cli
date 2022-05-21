package org.asamk.signal.manager.util;

import com.google.protobuf.ByteString;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class PaymentUtils {

    private PaymentUtils() {
    }

    /**
     * Signs the supplied address bytes with the {@link IdentityKeyPair}'s private key and returns a proto that includes it, and it's signature.
     */
    public static SignalServiceProtos.PaymentAddress signPaymentsAddress(
            byte[] publicAddressBytes, ECPrivateKey privateKey
    ) {
        byte[] signature = privateKey.calculateSignature(publicAddressBytes);

        return SignalServiceProtos.PaymentAddress.newBuilder()
                .setMobileCoinAddress(SignalServiceProtos.PaymentAddress.MobileCoinAddress.newBuilder()
                        .setAddress(ByteString.copyFrom(publicAddressBytes))
                        .setSignature(ByteString.copyFrom(signature)))
                .build();
    }

    /**
     * Verifies that the payments address is signed with the supplied {@link IdentityKey}.
     * <p>
     * Returns the validated bytes if so, otherwise returns null.
     */
    public static byte[] verifyPaymentsAddress(
            SignalServiceProtos.PaymentAddress paymentAddress, ECPublicKey publicKey
    ) {
        if (!paymentAddress.hasMobileCoinAddress()) {
            return null;
        }

        byte[] bytes = paymentAddress.getMobileCoinAddress().getAddress().toByteArray();
        byte[] signature = paymentAddress.getMobileCoinAddress().getSignature().toByteArray();

        if (signature.length != 64 || !publicKey.verifySignature(bytes, signature)) {
            return null;
        }

        return bytes;
    }
}
