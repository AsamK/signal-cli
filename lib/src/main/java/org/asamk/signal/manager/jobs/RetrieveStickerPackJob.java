package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.JsonStickerPack;
import org.asamk.signal.manager.storage.stickers.StickerPackId;
import org.asamk.signal.manager.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.signalservice.internal.util.Hex;

import java.io.IOException;
import java.util.HashSet;
import java.util.stream.Collectors;

public class RetrieveStickerPackJob implements Job {

    private final static Logger logger = LoggerFactory.getLogger(RetrieveStickerPackJob.class);

    private final StickerPackId packId;
    private final byte[] packKey;

    public RetrieveStickerPackJob(final StickerPackId packId, final byte[] packKey) {
        this.packId = packId;
        this.packKey = packKey;
    }

    @Override
    public void run(Context context) {
        if (context.getStickerPackStore().existsStickerPack(packId)) {
            logger.debug("Sticker pack {} already downloaded.", Hex.toStringCondensed(packId.serialize()));
            return;
        }
        logger.debug("Retrieving sticker pack {}.", Hex.toStringCondensed(packId.serialize()));
        try {
            final var manifest = context.getMessageReceiver().retrieveStickerManifest(packId.serialize(), packKey);

            final var stickerIds = new HashSet<Integer>();
            if (manifest.getCover().isPresent()) {
                stickerIds.add(manifest.getCover().get().getId());
            }
            for (var sticker : manifest.getStickers()) {
                stickerIds.add(sticker.getId());
            }

            for (var id : stickerIds) {
                final var inputStream = context.getMessageReceiver().retrieveSticker(packId.serialize(), packKey, id);
                context.getStickerPackStore().storeSticker(packId, id, o -> IOUtils.copyStream(inputStream, o));
            }

            final var jsonManifest = new JsonStickerPack(manifest.getTitle().orNull(),
                    manifest.getAuthor().orNull(),
                    manifest.getCover()
                            .transform(c -> new JsonStickerPack.JsonSticker(c.getEmoji(),
                                    String.valueOf(c.getId()),
                                    c.getContentType()))
                            .orNull(),
                    manifest.getStickers()
                            .stream()
                            .map(c -> new JsonStickerPack.JsonSticker(c.getEmoji(),
                                    String.valueOf(c.getId()),
                                    c.getContentType()))
                            .collect(Collectors.toList()));
            context.getStickerPackStore().storeManifest(packId, jsonManifest);
        } catch (IOException e) {
            logger.warn("Failed to retrieve sticker pack {}: {}",
                    Hex.toStringCondensed(packId.serialize()),
                    e.getMessage());
        } catch (InvalidMessageException e) {
            logger.warn("Failed to retrieve sticker pack {}, invalid pack data: {}",
                    Hex.toStringCondensed(packId.serialize()),
                    e.getMessage());
        }
    }
}
