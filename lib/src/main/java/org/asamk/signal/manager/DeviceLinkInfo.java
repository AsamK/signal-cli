package org.asamk.signal.manager;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.whispersystems.signalservice.internal.util.Util.isEmpty;

public class DeviceLinkInfo {

    final String deviceIdentifier;
    final ECPublicKey deviceKey;

    public static DeviceLinkInfo parseDeviceLinkUri(URI linkUri) throws InvalidKeyException {
        final var rawQuery = linkUri.getRawQuery();
        if (isEmpty(rawQuery)) {
            throw new RuntimeException("Invalid device link uri");
        }

        var query = getQueryMap(rawQuery);
        var deviceIdentifier = query.get("uuid");
        var publicKeyEncoded = query.get("pub_key");

        if (isEmpty(deviceIdentifier) || isEmpty(publicKeyEncoded)) {
            throw new RuntimeException("Invalid device link uri");
        }

        final byte[] publicKeyBytes;
        try {
            publicKeyBytes = Base64.getDecoder().decode(publicKeyEncoded);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid device link uri", e);
        }
        var deviceKey = Curve.decodePoint(publicKeyBytes, 0);

        return new DeviceLinkInfo(deviceIdentifier, deviceKey);
    }

    private static Map<String, String> getQueryMap(String query) {
        var params = query.split("&");
        var map = new HashMap<String, String>();
        for (var param : params) {
            final var paramParts = param.split("=");
            var name = URLDecoder.decode(paramParts[0], StandardCharsets.UTF_8);
            var value = URLDecoder.decode(paramParts[1], StandardCharsets.UTF_8);
            map.put(name, value);
        }
        return map;
    }

    public DeviceLinkInfo(final String deviceIdentifier, final ECPublicKey deviceKey) {
        this.deviceIdentifier = deviceIdentifier;
        this.deviceKey = deviceKey;
    }

    public URI createDeviceLinkUri() {
        final var deviceKeyString = Base64.getEncoder().encodeToString(deviceKey.serialize()).replace("=", "");
        try {
            return new URI("tsdevice:/?uuid="
                    + URLEncoder.encode(deviceIdentifier, StandardCharsets.UTF_8)
                    + "&pub_key="
                    + URLEncoder.encode(deviceKeyString, StandardCharsets.UTF_8));
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }
}
