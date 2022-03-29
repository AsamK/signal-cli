package org.asamk.signal.manager.storage.stickerPacks;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.Utils;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.util.Hex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class StickerPackStore {

    private final File stickersPath;

    public StickerPackStore(final File stickersPath) {
        this.stickersPath = stickersPath;
    }

    public boolean existsStickerPack(StickerPackId stickerPackId) {
        return getStickerPackManifestFile(stickerPackId).exists();
    }

    public JsonStickerPack retrieveManifest(StickerPackId stickerPackId) throws IOException {
        try (final var inputStream = new FileInputStream(getStickerPackManifestFile(stickerPackId))) {
            return new ObjectMapper().readValue(inputStream, JsonStickerPack.class);
        }
    }

    public StreamDetails retrieveSticker(final StickerPackId stickerPackId, final int stickerId) throws IOException {
        final var stickerFile = getStickerPackStickerFile(stickerPackId, stickerId);
        if (!stickerFile.exists()) {
            return null;
        }
        return Utils.createStreamDetailsFromFile(stickerFile);
    }

    public void storeManifest(StickerPackId stickerPackId, JsonStickerPack manifest) throws IOException {
        try (final var output = new FileOutputStream(getStickerPackManifestFile(stickerPackId))) {
            try (var writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                new ObjectMapper().writeValue(writer, manifest);
            }
        }
    }

    public void storeSticker(StickerPackId stickerPackId, int stickerId, StickerStorer storer) throws IOException {
        createStickerPackDir(stickerPackId);
        try (final var output = new FileOutputStream(getStickerPackStickerFile(stickerPackId, stickerId))) {
            storer.store(output);
        }
    }

    private File getStickerPackManifestFile(StickerPackId stickerPackId) {
        return new File(getStickerPackPath(stickerPackId), "manifest.json");
    }

    private File getStickerPackStickerFile(StickerPackId stickerPackId, int stickerId) {
        return new File(getStickerPackPath(stickerPackId), String.valueOf(stickerId));
    }

    private File getStickerPackPath(StickerPackId stickerPackId) {
        return new File(stickersPath, Hex.toStringCondensed(stickerPackId.serialize()));
    }

    private void createStickerPackDir(StickerPackId stickerPackId) throws IOException {
        IOUtils.createPrivateDirectories(getStickerPackPath(stickerPackId));
    }

    @FunctionalInterface
    public interface StickerStorer {

        void store(OutputStream outputStream) throws IOException;
    }
}
