package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.Base64;

public record JsonSticker(String packId, String packKey, int stickerId) {

    static JsonSticker from(MessageEnvelope.Data.Sticker sticker) {
        final var encoder = Base64.getEncoder();
        final var packId = encoder.encodeToString(sticker.packId());
        final var packKey = encoder.encodeToString(sticker.packKey());
        final var stickerId = sticker.stickerId();
        return new JsonSticker(packId, packKey, stickerId);
    }
}
