package org.asamk.signal.manager.api;

import org.asamk.signal.manager.util.Utils;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPublicKey;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.whispersystems.signalservice.internal.util.Util.isEmpty;

public record DeviceLinkUrl(String deviceIdentifier, ECPublicKey deviceKey) {

    public static DeviceLinkUrl parseDeviceLinkUri(URI linkUri) throws InvalidDeviceLinkException {
        final var rawQuery = linkUri.getRawQuery();
        if (isEmpty(rawQuery)) {
            throw new RuntimeException("Invalid device link uri");
        }

        var query = Utils.getQueryMap(rawQuery);
        var deviceIdentifier = query.get("uuid");
        var publicKeyEncoded = query.get("pub_key");

        if (isEmpty(deviceIdentifier) || isEmpty(publicKeyEncoded)) {
            throw new InvalidDeviceLinkException("Invalid device link uri");
        }

        final byte[] publicKeyBytes;
        try {
            publicKeyBytes = Base64.getDecoder().decode(publicKeyEncoded);
        } catch (IllegalArgumentException e) {
            throw new InvalidDeviceLinkException("Invalid device link uri", e);
        }
        ECPublicKey deviceKey;
        try {
            deviceKey = Curve.decodePoint(publicKeyBytes, 0);
        } catch (InvalidKeyException e) {
            throw new InvalidDeviceLinkException("Invalid device link", e);
        }

        return new DeviceLinkUrl(deviceIdentifier, deviceKey);
    }

    public URI createDeviceLinkUri() {
        final var deviceKeyString = Base64.getEncoder().encodeToString(deviceKey.serialize()).replace("=", "");
        try {
            return new URI("sgnl://linkdevice?uuid="
                    + URLEncoder.encode(deviceIdentifier, StandardCharsets.UTF_8)
                    + "&pub_key="
                    + URLEncoder.encode(deviceKeyString, StandardCharsets.UTF_8));
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }
}
