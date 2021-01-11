package org.asamk.signal.manager;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.whispersystems.signalservice.internal.util.Util.isEmpty;

public class DeviceLinkInfo {

    final String deviceIdentifier;
    final ECPublicKey deviceKey;

    public static DeviceLinkInfo parseDeviceLinkUri(URI linkUri) throws IOException, InvalidKeyException {
        final String rawQuery = linkUri.getRawQuery();
        if (isEmpty(rawQuery)) {
            throw new RuntimeException("Invalid device link uri");
        }

        Map<String, String> query = getQueryMap(rawQuery);
        String deviceIdentifier = query.get("uuid");
        String publicKeyEncoded = query.get("pub_key");

        if (isEmpty(deviceIdentifier) || isEmpty(publicKeyEncoded)) {
            throw new RuntimeException("Invalid device link uri");
        }

        ECPublicKey deviceKey = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);

        return new DeviceLinkInfo(deviceIdentifier, deviceKey);
    }

    private static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            final String[] paramParts = param.split("=");
            String name = URLDecoder.decode(paramParts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(paramParts[1], StandardCharsets.UTF_8);
            map.put(name, value);
        }
        return map;
    }

    public DeviceLinkInfo(final String deviceIdentifier, final ECPublicKey deviceKey) {
        this.deviceIdentifier = deviceIdentifier;
        this.deviceKey = deviceKey;
    }

    public String createDeviceLinkUri() {
        return "tsdevice:/?uuid="
                + URLEncoder.encode(deviceIdentifier, StandardCharsets.UTF_8)
                + "&pub_key="
                + URLEncoder.encode(Base64.encodeBytesWithoutPadding(deviceKey.serialize()), StandardCharsets.UTF_8);
    }
}
