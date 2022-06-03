package org.asamk.signal.manager.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data URI record that follows RFC 2397.
 *
 * @param mediaType the media type. If empty, the default media type "text/plain" is used.
 * @param parameter the list of parameters. Must be URL escaped encoded.
 * @param data      the data. If base64 is not given, the data is treated as ASCII string.
 */
@SuppressWarnings({"java:S6218"})
public record DataURI(String mediaType, Map<String, String> parameter, byte[] data) {

    public static final Pattern DATA_URI_PATTERN = Pattern.compile(
            "data:(?<type>.+?\\/.+?)?(?<parameters>;.+?)?(?<base64>;base64)?,(?<data>.+)",
            Pattern.CASE_INSENSITIVE);
    public static final Pattern PARAMETER_PATTERN = Pattern.compile("\\G;(?<key>.+)=(?<value>.+)",
            Pattern.CASE_INSENSITIVE);
    public static final String DEFAULT_TYPE = "text/plain";

    /**
     * Generates a new {@link DataURI} object from the given string.
     *
     * @param dataURI the data URI
     * @return a data URI object
     * @throws IllegalArgumentException if the given string is not a valid data URI
     */
    public static DataURI of(final String dataURI) {
        final var matcher = DATA_URI_PATTERN.matcher(dataURI);

        if (!matcher.find()) {
            throw new IllegalArgumentException("The given string is not a valid data URI.");
        }

        final Map<String, String> parameters = new HashMap<>();
        final var params = matcher.group("parameters");
        if (params != null) {
            final Matcher paramsMatcher = PARAMETER_PATTERN.matcher(params);
            while (paramsMatcher.find()) {
                final var key = paramsMatcher.group("key");
                final var value = URLDecoder.decode(paramsMatcher.group("value"), StandardCharsets.UTF_8);
                parameters.put(key, value);
            }
        }

        final boolean isBase64 = matcher.group("base64") != null;
        final byte[] data;
        if (isBase64) {
            data = Base64.getDecoder().decode(matcher.group("data").getBytes(StandardCharsets.UTF_8));
        } else {
            data = URLDecoder.decode(matcher.group("data"), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        }

        return new DataURI(Optional.ofNullable(matcher.group("type")).orElse(DEFAULT_TYPE), parameters, data);
    }
}
