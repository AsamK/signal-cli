package org.asamk.signal.manager.api;

import java.io.File;

public class UpdateProfile {

    private final String givenName;
    private final String familyName;
    private final String about;
    private final String aboutEmoji;
    private final File avatar;
    private final boolean deleteAvatar;

    private UpdateProfile(final Builder builder) {
        givenName = builder.givenName;
        familyName = builder.familyName;
        about = builder.about;
        aboutEmoji = builder.aboutEmoji;
        avatar = builder.avatar;
        deleteAvatar = builder.deleteAvatar;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(final UpdateProfile copy) {
        Builder builder = new Builder();
        builder.givenName = copy.getGivenName();
        builder.familyName = copy.getFamilyName();
        builder.about = copy.getAbout();
        builder.aboutEmoji = copy.getAboutEmoji();
        builder.avatar = copy.getAvatar();
        builder.deleteAvatar = copy.isDeleteAvatar();
        return builder;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public String getAbout() {
        return about;
    }

    public String getAboutEmoji() {
        return aboutEmoji;
    }

    public File getAvatar() {
        return avatar;
    }

    public boolean isDeleteAvatar() {
        return deleteAvatar;
    }

    public static final class Builder {

        private String givenName;
        private String familyName;
        private String about;
        private String aboutEmoji;
        private File avatar;
        private boolean deleteAvatar;

        private Builder() {
        }

        public Builder withGivenName(final String val) {
            givenName = val;
            return this;
        }

        public Builder withFamilyName(final String val) {
            familyName = val;
            return this;
        }

        public Builder withAbout(final String val) {
            about = val;
            return this;
        }

        public Builder withAboutEmoji(final String val) {
            aboutEmoji = val;
            return this;
        }

        public Builder withAvatar(final File val) {
            avatar = val;
            return this;
        }

        public Builder withDeleteAvatar(final boolean val) {
            deleteAvatar = val;
            return this;
        }

        public UpdateProfile build() {
            return new UpdateProfile(this);
        }
    }
}
