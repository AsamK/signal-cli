package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.Base64;

public record JsonSticker(String packId, String packKey, int stickerId) {

    static JsonSticker from(SignalServiceDataMessage.Sticker sticker) {
        final var packId = Base64.getEncoder().encodeToString(sticker.getPackId());
        final var packKey = Base64.getEncoder().encodeToString(sticker.getPackKey());
        final var stickerId = sticker.getStickerId();
        return new JsonSticker(packId, packKey, stickerId);
    }
}
