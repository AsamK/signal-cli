package org.asamk.signal.manager.internal;

import java.io.File;

public record PathConfig(
        File dataPath, File attachmentsPath, File avatarsPath, File stickerPacksPath
) {

    public static PathConfig createDefault(final File settingsPath) {
        return new PathConfig(new File(settingsPath, "data"),
                new File(settingsPath, "attachments"),
                new File(settingsPath, "avatars"),
                new File(settingsPath, "stickers"));
    }
}
