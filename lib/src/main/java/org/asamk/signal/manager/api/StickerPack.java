package org.asamk.signal.manager.api;

import java.util.List;
import java.util.Optional;

public record StickerPack(
        StickerPackId packId,
        StickerPackUrl url,
        boolean installed,
        String title,
        String author,
        Optional<Sticker> cover,
        List<Sticker> stickers
) {

    public StickerPack(final StickerPackId packId, final byte[] packKey, final boolean installed) {
        this(packId, new StickerPackUrl(packId, packKey), installed, "", "", Optional.empty(), List.of());
    }

    public record Sticker(int id, String emoji, String contentType) {}
}
