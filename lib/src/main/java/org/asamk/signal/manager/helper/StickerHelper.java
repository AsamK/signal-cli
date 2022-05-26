package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.api.InvalidStickerException;
import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.stickerPacks.JsonStickerPack;
import org.asamk.signal.manager.util.IOUtils;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.internal.util.Hex;

import java.io.IOException;
import java.util.HashSet;

public class StickerHelper {

    private final static Logger logger = LoggerFactory.getLogger(StickerHelper.class);

    private final Context context;
    private final SignalAccount account;
    private final SignalDependencies dependencies;

    public StickerHelper(final Context context) {
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
        this.context = context;
    }

    public JsonStickerPack getOrRetrieveStickerPack(
            StickerPackId packId, byte[] packKey
    ) throws InvalidStickerException {
        if (!context.getStickerPackStore().existsStickerPack(packId)) {
            try {
                retrieveStickerPack(packId, packKey);
            } catch (InvalidMessageException | IOException e) {
                throw new InvalidStickerException("Failed to retrieve sticker pack");
            }
        }
        final JsonStickerPack manifest;
        try {
            manifest = context.getStickerPackStore().retrieveManifest(packId);
        } catch (IOException e) {
            throw new InvalidStickerException("Failed to load sticker pack manifest");
        }
        return manifest;
    }

    public void retrieveStickerPack(StickerPackId packId, byte[] packKey) throws InvalidMessageException, IOException {
        logger.debug("Retrieving sticker pack {}.", Hex.toStringCondensed(packId.serialize()));
        final var messageReceiver = dependencies.getMessageReceiver();
        final var manifest = messageReceiver.retrieveStickerManifest(packId.serialize(), packKey);

        final var stickerIds = new HashSet<Integer>();
        if (manifest.getCover().isPresent()) {
            stickerIds.add(manifest.getCover().get().getId());
        }
        for (var sticker : manifest.getStickers()) {
            stickerIds.add(sticker.getId());
        }

        for (var id : stickerIds) {
            try (final var inputStream = messageReceiver.retrieveSticker(packId.serialize(), packKey, id)) {
                context.getStickerPackStore().storeSticker(packId, id, o -> IOUtils.copyStream(inputStream, o));
            }
        }

        final var jsonManifest = new JsonStickerPack(manifest.getTitle().orElse(null),
                manifest.getAuthor().orElse(null),
                manifest.getCover()
                        .map(c -> new JsonStickerPack.JsonSticker(c.getId(),
                                c.getEmoji(),
                                String.valueOf(c.getId()),
                                c.getContentType()))
                        .orElse(null),
                manifest.getStickers()
                        .stream()
                        .map(c -> new JsonStickerPack.JsonSticker(c.getId(),
                                c.getEmoji(),
                                String.valueOf(c.getId()),
                                c.getContentType()))
                        .toList());
        context.getStickerPackStore().storeManifest(packId, jsonManifest);
    }
}
