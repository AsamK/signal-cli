package org.asamk.signal.manager;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class JsonStickerPack {

    @JsonProperty
    public String title;

    @JsonProperty
    public String author;

    @JsonProperty
    public JsonSticker cover;

    @JsonProperty
    public List<JsonSticker> stickers;

    // For deserialization
    private JsonStickerPack() {
    }

    public JsonStickerPack(
            final String title, final String author, final JsonSticker cover, final List<JsonSticker> stickers
    ) {
        this.title = title;
        this.author = author;
        this.cover = cover;
        this.stickers = stickers;
    }

    public static class JsonSticker {

        @JsonProperty
        public String emoji;

        @JsonProperty
        public String file;

        @JsonProperty
        public String contentType;

        // For deserialization
        private JsonSticker() {
        }

        public JsonSticker(final String emoji, final String file, final String contentType) {
            this.emoji = emoji;
            this.file = file;
            this.contentType = contentType;
        }
    }
}
