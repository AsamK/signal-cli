package org.asamk.signal.manager;

public class PathConfig {

    private final String dataPath;
    private final String attachmentsPath;
    private final String avatarsPath;

    public static PathConfig createDefault(final String settingsPath) {
        return new PathConfig(settingsPath + "/data", settingsPath + "/attachments", settingsPath + "/avatars");
    }

    private PathConfig(final String dataPath, final String attachmentsPath, final String avatarsPath) {
        this.dataPath = dataPath;
        this.attachmentsPath = attachmentsPath;
        this.avatarsPath = avatarsPath;
    }

    public String getDataPath() {
        return dataPath;
    }

    public String getAttachmentsPath() {
        return attachmentsPath;
    }

    public String getAvatarsPath() {
        return avatarsPath;
    }
}
