package org.asamk.signal.manager.api;

import org.asamk.signal.manager.util.Utils;
import org.whispersystems.signalservice.internal.util.Hex;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.whispersystems.signalservice.internal.util.Util.isEmpty;

public record StickerPackUrl(StickerPackId packId, byte[] packKey) {

    /**
     * @throws InvalidStickerPackLinkException If url cannot be parsed.
     */
    public static StickerPackUrl fromUri(URI uri) throws InvalidStickerPackLinkException {
        final var rawQuery = uri.getRawFragment();
        if (isEmpty(rawQuery)) {
            throw new InvalidStickerPackLinkException("Invalid sticker pack uri");
        }

        var query = Utils.getQueryMap(rawQuery);
        var packIdString = query.get("pack_id");
        var packKeyString = query.get("pack_key");

        if (isEmpty(packIdString) || isEmpty(packKeyString)) {
            throw new InvalidStickerPackLinkException("Incomplete sticker pack uri");
        }

        StickerPackId packId;
        try {
            packId = StickerPackId.deserialize(Hex.fromStringCondensed(packIdString));
        } catch (IOException e) {
            throw new InvalidStickerPackLinkException("Invalid sticker pack", e);
        }
        final byte[] packKey;
        try {
            packKey = Hex.fromStringCondensed(packKeyString);
        } catch (IOException e) {
            throw new InvalidStickerPackLinkException("Invalid sticker pack uri", e);
        }
        return new StickerPackUrl(packId, packKey);
    }

    public URI getUrl() {
        try {
            return new URI("https",
                    "signal.art",
                    "/addstickers/",
                    "pack_id="
                            + URLEncoder.encode(Hex.toStringCondensed(packId.serialize()), StandardCharsets.UTF_8)
                            + "&pack_key="
                            + URLEncoder.encode(Hex.toStringCondensed(packKey), StandardCharsets.UTF_8));
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    public final static class InvalidStickerPackLinkException extends Exception {

        public InvalidStickerPackLinkException(String message) {
            super(message);
        }

        public InvalidStickerPackLinkException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
