package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.Base64;

public class JsonSticker {

    String packId;
    String packKey;
    int stickerId;

    public JsonSticker(SignalServiceDataMessage.Sticker sticker) {
        this.packId = Base64.getEncoder().encodeToString(sticker.getPackId());
        this.packKey = Base64.getEncoder().encodeToString(sticker.getPackKey());
        this.stickerId = sticker.getStickerId();
        // TODO also download sticker image ??
    }
}
