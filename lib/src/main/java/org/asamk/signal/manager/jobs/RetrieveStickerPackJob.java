package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.manager.helper.Context;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.internal.util.Hex;

import java.io.IOException;

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
        try {
            context.getStickerHelper().retrieveStickerPack(packId, packKey);
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
