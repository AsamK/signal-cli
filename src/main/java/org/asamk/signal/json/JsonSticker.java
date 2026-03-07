package org.asamk.signal.json;

import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Hex;

@JsonSchema(title = "Sticker")
public record JsonSticker(String packId, int stickerId) {

    static JsonSticker from(MessageEnvelope.Data.Sticker sticker) {
        final var packId = Hex.toStringCondensed(sticker.packId().serialize());
        final var stickerId = sticker.stickerId();
        return new JsonSticker(packId, stickerId);
    }
}
