package org.asamk.signal.manager.storage.stickers;

import java.util.Arrays;

public class StickerPackId {

    private final byte[] id;

    private StickerPackId(final byte[] id) {
        this.id = id;
    }

    public static StickerPackId deserialize(byte[] packId) {
        return new StickerPackId(packId);
    }

    public byte[] serialize() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final StickerPackId that = (StickerPackId) o;

        return Arrays.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(id);
    }
}
