package org.asamk.signal.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Util {

    private Util() {
    }

    public static String getStringIfNotBlank(Optional<String> value) {
        var string = value.orElse(null);
        if (string == null || string.isBlank()) {
            return null;
        }
        return string;
    }

    public static String dashSeparatedToCamelCaseString(String s) {
        var parts = s.split("-");
        return toCamelCaseString(Arrays.asList(parts));
    }

    private static String toCamelCaseString(List<String> strings) {
        if (strings.size() == 0) {
            return "";
        }
        return strings.get(0) + strings.stream()
                .skip(1)
                .filter(s -> s.length() > 0)
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining());
    }

    public static String formatSafetyNumber(String digits) {
        if (digits == null) {
            return null;
        }

        final var partCount = 12;
        var partSize = digits.length() / partCount;
        var f = new StringBuilder(digits.length() + partCount);
        for (var i = 0; i < partCount; i++) {
            f.append(digits, i * partSize, (i * partSize) + partSize);
            if (i != partCount - 1) {
                f.append(" ");
            }
        }
        return f.toString();
    }

    public static ObjectMapper createJsonObjectMapper() {
        var objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        objectMapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        return objectMapper;
    }

    public static Map<String, String> getQueryMap(String query) {
        var params = query.split("&");
        var map = new HashMap<String, String>();
        for (var param : params) {
            final var paramParts = param.split("=");
            var name = URLDecoder.decode(paramParts[0], StandardCharsets.UTF_8);
            var value = paramParts.length == 1 ? null : URLDecoder.decode(paramParts[1], StandardCharsets.UTF_8);
            map.put(name, value);
        }
        return map;
    }

}
