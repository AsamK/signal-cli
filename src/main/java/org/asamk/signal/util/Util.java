package org.asamk.signal.util;

import com.fasterxml.jackson.databind.JsonNode;

import org.asamk.signal.manager.GroupId;
import org.asamk.signal.manager.GroupIdFormatException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.InvalidObjectException;

public class Util {

    private Util() {
    }

    public static String formatSafetyNumber(String digits) {
        final int partCount = 12;
        int partSize = digits.length() / partCount;
        StringBuilder f = new StringBuilder(digits.length() + partCount);
        for (int i = 0; i < partCount; i++) {
            f.append(digits, i * partSize, (i * partSize) + partSize).append(" ");
        }
        return f.toString();
    }

    public static String join(CharSequence separator, Iterable<? extends CharSequence> list) {
        StringBuilder buf = new StringBuilder();
        for (CharSequence str : list) {
            if (buf.length() > 0) {
                buf.append(separator);
            }
            buf.append(str);
        }

        return buf.toString();
    }

    public static JsonNode getNotNullNode(JsonNode parent, String name) throws InvalidObjectException {
        JsonNode node = parent.get(name);
        if (node == null) {
            throw new InvalidObjectException(String.format("Incorrect file format: expected parameter %s not found ",
                    name));
        }

        return node;
    }

    public static GroupId decodeGroupId(String groupId) throws GroupIdFormatException {
        return GroupId.fromBase64(groupId);
    }

    public static String canonicalizeNumber(String number, String localNumber) throws InvalidNumberException {
        return PhoneNumberFormatter.formatNumber(number, localNumber);
    }

    public static SignalServiceAddress getSignalServiceAddressFromIdentifier(final String identifier) {
        if (UuidUtil.isUuid(identifier)) {
            return new SignalServiceAddress(UuidUtil.parseOrNull(identifier), null);
        } else {
            return new SignalServiceAddress(null, identifier);
        }
    }
}
