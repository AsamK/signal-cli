package org.asamk.signal.util;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

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
}
