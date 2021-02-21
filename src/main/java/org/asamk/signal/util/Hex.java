package org.asamk.signal.util;

public class Hex {

    private final static char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private Hex() {
    }

    public static String toString(byte[] bytes) {
        var buf = new StringBuffer();
        for (final var aByte : bytes) {
            appendHexChar(buf, aByte);
            buf.append(" ");
        }
        return buf.toString();
    }

    public static String toStringCondensed(byte[] bytes) {
        var buf = new StringBuffer();
        for (final var aByte : bytes) {
            appendHexChar(buf, aByte);
        }
        return buf.toString();
    }

    private static void appendHexChar(StringBuffer buf, int b) {
        buf.append(HEX_DIGITS[(b >> 4) & 0xf]);
        buf.append(HEX_DIGITS[b & 0xf]);
    }

    public static byte[] toByteArray(String s) {
        var len = s.length();
        var data = new byte[len / 2];
        for (var i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
