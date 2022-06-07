package org.asamk.signal.manager.storage.stickers;

import org.asamk.signal.manager.api.StickerPackId;

public record StickerPack(long internalId, StickerPackId packId, byte[] packKey, boolean isInstalled) {

    public StickerPack(final StickerPackId packId, final byte[] packKey) {
        this(-1, packId, packKey, false);
    }
}
