package org.asamk.signal.manager;

import java.io.File;

public class PathConfig {

    private final File dataPath;
    private final File attachmentsPath;
    private final File avatarsPath;

    public static PathConfig createDefault(final File settingsPath) {
        return new PathConfig(new File(settingsPath, "data"),
                new File(settingsPath, "attachments"),
                new File(settingsPath, "avatars"));
    }

    private PathConfig(final File dataPath, final File attachmentsPath, final File avatarsPath) {
        this.dataPath = dataPath;
        this.attachmentsPath = attachmentsPath;
        this.avatarsPath = avatarsPath;
    }

    public File getDataPath() {
        return dataPath;
    }

    public File getAttachmentsPath() {
        return attachmentsPath;
    }

    public File getAvatarsPath() {
        return avatarsPath;
    }
}
