package org.asamk.signal.manager.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.StickerPackInvalidException;
import org.asamk.signal.manager.storage.stickerPacks.JsonStickerPack;
import org.whispersystems.signalservice.api.messages.SignalServiceStickerManifestUpload;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Optional;
import java.util.zip.ZipFile;

public class StickerUtils {

    public static SignalServiceStickerManifestUpload getSignalServiceStickerManifestUpload(
            final File file
    ) throws IOException, StickerPackInvalidException {
        ZipFile zip = null;
        String rootPath = null;

        if (file.getName().endsWith(".zip")) {
            zip = new ZipFile(file);
        } else if (file.getName().equals("manifest.json")) {
            rootPath = file.getParent();
        } else {
            throw new StickerPackInvalidException("Could not find manifest.json");
        }

        var pack = parseStickerPack(rootPath, zip);

        if (pack.stickers() == null) {
            throw new StickerPackInvalidException("Must set a 'stickers' field.");
        }

        if (pack.stickers().isEmpty()) {
            throw new StickerPackInvalidException("Must include stickers.");
        }

        var stickers = new ArrayList<SignalServiceStickerManifestUpload.StickerInfo>(pack.stickers().size());
        for (var sticker : pack.stickers()) {
            if (sticker.file() == null) {
                throw new StickerPackInvalidException("Must set a 'file' field on each sticker.");
            }

            Pair<InputStream, Long> data;
            try {
                data = getInputStreamAndLength(rootPath, zip, sticker.file());
            } catch (IOException ignored) {
                throw new StickerPackInvalidException("Could not find find " + sticker.file());
            }

            var contentType = sticker.contentType() != null && !sticker.contentType().isEmpty()
                    ? sticker.contentType()
                    : getContentType(rootPath, zip, sticker.file());
            var stickerInfo = new SignalServiceStickerManifestUpload.StickerInfo(data.first(),
                    data.second(),
                    Optional.ofNullable(sticker.emoji()).orElse(""),
                    contentType);
            stickers.add(stickerInfo);
        }

        SignalServiceStickerManifestUpload.StickerInfo cover = null;
        if (pack.cover() != null) {
            if (pack.cover().file() == null) {
                throw new StickerPackInvalidException("Must set a 'file' field on the cover.");
            }

            Pair<InputStream, Long> data;
            try {
                data = getInputStreamAndLength(rootPath, zip, pack.cover().file());
            } catch (IOException ignored) {
                throw new StickerPackInvalidException("Could not find find " + pack.cover().file());
            }

            var contentType = pack.cover().contentType() != null && !pack.cover().contentType().isEmpty() ? pack.cover()
                    .contentType() : getContentType(rootPath, zip, pack.cover().file());
            cover = new SignalServiceStickerManifestUpload.StickerInfo(data.first(),
                    data.second(),
                    Optional.ofNullable(pack.cover().emoji()).orElse(""),
                    contentType);
        }

        return new SignalServiceStickerManifestUpload(pack.title(), pack.author(), cover, stickers);
    }

    private static JsonStickerPack parseStickerPack(String rootPath, ZipFile zip) throws IOException {
        InputStream inputStream;
        if (zip != null) {
            inputStream = zip.getInputStream(zip.getEntry("manifest.json"));
        } else {
            inputStream = new FileInputStream((new File(rootPath, "manifest.json")));
        }
        return new ObjectMapper().readValue(inputStream, JsonStickerPack.class);
    }

    private static Pair<InputStream, Long> getInputStreamAndLength(
            final String rootPath,
            final ZipFile zip,
            final String subfile
    ) throws IOException {
        if (zip != null) {
            final var entry = zip.getEntry(subfile);
            return new Pair<>(zip.getInputStream(entry), entry.getSize());
        } else {
            final var file = new File(rootPath, subfile);
            return new Pair<>(new FileInputStream(file), file.length());
        }
    }

    private static String getContentType(
            final String rootPath,
            final ZipFile zip,
            final String subfile
    ) throws IOException {
        if (zip != null) {
            final var entry = zip.getEntry(subfile);
            try (InputStream bufferedStream = new BufferedInputStream(zip.getInputStream(entry))) {
                return MimeUtils.getStreamMimeType(bufferedStream).orElse(null);
            }
        } else {
            final var file = new File(rootPath, subfile);
            return MimeUtils.getFileMimeType(file).orElse(null);
        }
    }
}
