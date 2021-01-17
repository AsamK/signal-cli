package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.Base64;

public class JsonSticker {

    @JsonProperty
    final String packId;

    @JsonProperty
    final String packKey;

    @JsonProperty
    final int stickerId;

    public JsonSticker(SignalServiceDataMessage.Sticker sticker) {
        this.packId = Base64.getEncoder().encodeToString(sticker.getPackId());
        this.packKey = Base64.getEncoder().encodeToString(sticker.getPackKey());
        this.stickerId = sticker.getStickerId();
    }
}
