package org.asamk.signal.manager;

import java.util.List;

public record JsonStickerPack(String title, String author, JsonSticker cover, List<JsonSticker> stickers) {

    public record JsonSticker(String emoji, String file, String contentType) {}
}
