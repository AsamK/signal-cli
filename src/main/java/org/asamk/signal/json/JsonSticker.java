package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.util.Base64;

public class JsonSticker {

    String packId;
    String packKey;
    int stickerId;

    public JsonSticker(SignalServiceDataMessage.Sticker sticker) {
        this.packId = Base64.encodeBytes(sticker.getPackId());
        this.packKey = Base64.encodeBytes(sticker.getPackKey());
        this.stickerId = sticker.getStickerId();
        // TODO also download sticker image ??
    }
}
