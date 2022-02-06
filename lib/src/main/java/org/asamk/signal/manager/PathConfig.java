package org.asamk.signal.manager;

import java.io.File;

record PathConfig(
        File dataPath, File attachmentsPath, File avatarsPath, File stickerPacksPath
) {

    static PathConfig createDefault(final File settingsPath) {
        return new PathConfig(new File(settingsPath, "data"),
                new File(settingsPath, "attachments"),
                new File(settingsPath, "avatars"),
                new File(settingsPath, "stickers"));
    }
}
