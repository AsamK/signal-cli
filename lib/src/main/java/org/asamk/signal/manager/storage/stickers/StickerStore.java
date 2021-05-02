package org.asamk.signal.manager.storage.stickers;

import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class StickerStore {

    private final Map<StickerPackId, Sticker> stickers;

    private final Saver saver;

    public StickerStore(final Saver saver) {
        this.saver = saver;
        stickers = new HashMap<>();
    }

    public StickerStore(final Map<StickerPackId, Sticker> stickers, final Saver saver) {
        this.stickers = stickers;
        this.saver = saver;
    }

    public static StickerStore fromStorage(Storage storage, Saver saver) {
        final var packIds = new HashSet<StickerPackId>();
        final var stickers = storage.stickers.stream().map(s -> {
            var packId = StickerPackId.deserialize(Base64.getDecoder().decode(s.packId));
            if (packIds.contains(packId)) {
                // Remove legacy duplicate packIds ...
                return null;
            }
            packIds.add(packId);
            var packKey = Base64.getDecoder().decode(s.packKey);
            var installed = s.installed;
            return new Sticker(packId, packKey, installed);
        }).filter(Objects::nonNull).collect(Collectors.toMap(Sticker::getPackId, s -> s));

        return new StickerStore(stickers, saver);
    }

    public Sticker getSticker(StickerPackId packId) {
        synchronized (stickers) {
            return stickers.get(packId);
        }
    }

    public void updateSticker(Sticker sticker) {
        Storage storage;
        synchronized (stickers) {
            stickers.put(sticker.getPackId(), sticker);
            storage = toStorageLocked();
        }
        saver.save(storage);
    }

    private Storage toStorageLocked() {
        return new Storage(stickers.values()
                .stream()
                .map(s -> new Storage.Sticker(Base64.getEncoder().encodeToString(s.getPackId().serialize()),
                        Base64.getEncoder().encodeToString(s.getPackKey()),
                        s.isInstalled()))
                .collect(Collectors.toList()));
    }

    public static class Storage {

        public List<Storage.Sticker> stickers;

        // For deserialization
        private Storage() {
        }

        public Storage(final List<Sticker> stickers) {
            this.stickers = stickers;
        }

        private static class Sticker {

            public String packId;
            public String packKey;
            public boolean installed;

            // For deserialization
            private Sticker() {
            }

            public Sticker(final String packId, final String packKey, final boolean installed) {
                this.packId = packId;
                this.packKey = packKey;
                this.installed = installed;
            }
        }
    }

    public interface Saver {

        void save(Storage storage);
    }
}
