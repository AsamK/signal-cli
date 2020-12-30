package org.asamk.signal.storage.stickers;

public class Sticker {

    private final byte[] packId;
    private final byte[] packKey;
    private boolean installed;

    public Sticker(final byte[] packId, final byte[] packKey) {
        this.packId = packId;
        this.packKey = packKey;
    }

    public Sticker(final byte[] packId, final byte[] packKey, final boolean installed) {
        this.packId = packId;
        this.packKey = packKey;
        this.installed = installed;
    }

    public byte[] getPackId() {
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
