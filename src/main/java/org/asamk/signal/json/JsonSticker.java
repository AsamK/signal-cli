package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Hex;

@Schema(name = "Sticker")
public record JsonSticker(
        @Schema(required = true) String packId,
        @Schema(required = true) int stickerId
) {

    static JsonSticker from(MessageEnvelope.Data.Sticker sticker) {
        final var packId = Hex.toStringCondensed(sticker.packId().serialize());
        final var stickerId = sticker.stickerId();
        return new JsonSticker(packId, stickerId);
    }
}
