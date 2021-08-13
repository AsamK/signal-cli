package org.asamk.signal.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.dbus.DbusAttachment;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class Util {

    private Util() {
    }

    public static String getStringIfNotBlank(Optional<String> value) {
        var string = value.orNull();
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
        final var partCount = 12;
        var partSize = digits.length() / partCount;
        var f = new StringBuilder(digits.length() + partCount);
        for (var i = 0; i < partCount; i++) {
            f.append(digits, i * partSize, (i * partSize) + partSize).append(" ");
        }
        return f.toString();
    }

    public static GroupId decodeGroupId(String groupId) throws GroupIdFormatException {
        return GroupId.fromBase64(groupId);
    }

    public static String getLegacyIdentifier(final SignalServiceAddress address) {
        return address.getNumber().or(() -> address.getUuid().get().toString());
    }

    public static ObjectMapper createJsonObjectMapper() {
        var objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        objectMapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        return objectMapper;
    }
}
