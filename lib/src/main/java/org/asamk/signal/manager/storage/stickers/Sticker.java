package org.asamk.signal.manager.storage.stickers;

public class Sticker {

    private final StickerPackId packId;
    private final byte[] packKey;
    private boolean installed;

    public Sticker(final StickerPackId packId, final byte[] packKey) {
        this.packId = packId;
        this.packKey = packKey;
    }

    public Sticker(final StickerPackId packId, final byte[] packKey, final boolean installed) {
        this.packId = packId;
        this.packKey = packKey;
        this.installed = installed;
    }

    public StickerPackId getPackId() {
        return packId;
    }

    public byte[] getPackKey() {
        return packKey;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(final boolean installed) {
        this.installed = installed;
    }
}
