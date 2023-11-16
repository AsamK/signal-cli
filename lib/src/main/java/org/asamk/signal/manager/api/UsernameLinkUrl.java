package org.asamk.signal.manager.api;

import org.signal.core.util.Base64;
import org.whispersystems.signalservice.api.push.UsernameLinkComponents;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

public final class UsernameLinkUrl {

    private static final Pattern URL_REGEX = Pattern.compile("(https://)?signal.me/?#eu/([a-zA-Z0-9+\\-_/]+)");

    private static final String BASE_URL = "https://signal.me/#eu/";

    private final String url;
    private final UsernameLinkComponents usernameLinkComponents;

    public static UsernameLinkUrl fromUri(String url) throws InvalidUsernameLinkException {
        final var matcher = URL_REGEX.matcher(url);
        if (!matcher.matches()) {
            throw new InvalidUsernameLinkException("Invalid username link");
        }
        final var path = matcher.group(2);
        final byte[] allBytes;
        try {
            allBytes = Base64.decode(path);
        } catch (IOException e) {
            throw new InvalidUsernameLinkException("Invalid base64 encoding");
        }

        if (allBytes.length != 48) {
            throw new InvalidUsernameLinkException("Invalid username link");
        }

        final var entropy = Arrays.copyOfRange(allBytes, 0, 32);
        final var serverId = Arrays.copyOfRange(allBytes, 32, allBytes.length);
        final var serverIdUuid = UuidUtil.parseOrNull(serverId);
        if (serverIdUuid == null) {
            throw new InvalidUsernameLinkException("Invalid serverId");
        }

        return new UsernameLinkUrl(new UsernameLinkComponents(entropy, serverIdUuid));
    }

    public UsernameLinkUrl(UsernameLinkComponents usernameLinkComponents) {
        this.usernameLinkComponents = usernameLinkComponents;
        this.url = createUrl(usernameLinkComponents);
    }

    private static String createUrl(UsernameLinkComponents usernameLinkComponents) {
        final var entropy = usernameLinkComponents.getEntropy();
        final var serverId = UuidUtil.toByteArray(usernameLinkComponents.getServerId());

        final var combined = new byte[entropy.length + serverId.length];
        System.arraycopy(entropy, 0, combined, 0, entropy.length);
        System.arraycopy(serverId, 0, combined, entropy.length, serverId.length);

        final var base64 = Base64.encodeUrlSafeWithoutPadding(combined);
        return BASE_URL + base64;
    }

    public String getUrl() {
        return url;
    }

    public UsernameLinkComponents getComponents() {
        return usernameLinkComponents;
    }

    public static final class InvalidUsernameLinkException extends Exception {

        public InvalidUsernameLinkException(String message) {
            super(message);
        }

        public InvalidUsernameLinkException(Throwable cause) {
            super(cause);
        }
    }
}
