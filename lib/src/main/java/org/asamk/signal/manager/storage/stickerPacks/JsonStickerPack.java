package org.asamk.signal.manager.storage.stickerPacks;

import org.asamk.signal.manager.api.StickerPack;

import java.util.List;

public record JsonStickerPack(String title, String author, JsonSticker cover, List<JsonSticker> stickers) {

    public record JsonSticker(Integer id, String emoji, String file, String contentType) {

        public StickerPack.Sticker toApi() {
            return new StickerPack.Sticker(id == null ? Integer.parseInt(file) : id, emoji, contentType);
        }
    }
}
