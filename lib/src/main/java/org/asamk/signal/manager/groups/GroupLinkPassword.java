package org.asamk.signal.manager.groups;

import org.asamk.signal.manager.util.KeyUtils;

import java.util.Arrays;

public final class GroupLinkPassword {

    private static final int SIZE = 16;

    private final byte[] bytes;

    public static GroupLinkPassword createNew() {
        return new GroupLinkPassword(KeyUtils.getSecretBytes(SIZE));
    }

    public static GroupLinkPassword fromBytes(byte[] bytes) {
        return new GroupLinkPassword(bytes);
    }

    private GroupLinkPassword(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] serialize() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GroupLinkPassword)) {
            return false;
        }

        return Arrays.equals(bytes, ((GroupLinkPassword) other).bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
