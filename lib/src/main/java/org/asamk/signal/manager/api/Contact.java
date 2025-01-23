package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.internal.util.Util;

public record Contact(
        String givenName,
        String familyName,
        String nickName,
        String nickNameGivenName,
        String nickNameFamilyName,
        String note,
        String color,
        int messageExpirationTime,
        int messageExpirationTimeVersion,
        long muteUntil,
        boolean hideStory,
        boolean isBlocked,
        boolean isArchived,
        boolean isProfileSharingEnabled,
        boolean isHidden,
        Long unregisteredTimestamp
) {

    private Contact(final Builder builder) {
        this(builder.givenName,
                builder.familyName,
                builder.nickName,
                builder.nickNameGivenName,
                builder.nickNameFamilyName,
                builder.note,
                builder.color,
                builder.messageExpirationTime,
                builder.messageExpirationTimeVersion,
                builder.muteUntil,
                builder.hideStory,
                builder.isBlocked,
                builder.isArchived,
                builder.isProfileSharingEnabled,
                builder.isHidden,
                builder.unregisteredTimestamp);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(final Contact copy) {
        Builder builder = new Builder();
        builder.givenName = copy.givenName();
        builder.familyName = copy.familyName();
        builder.nickName = copy.nickName();
        builder.nickNameGivenName = copy.nickNameGivenName();
        builder.nickNameFamilyName = copy.nickNameFamilyName();
        builder.note = copy.note();
        builder.color = copy.color();
        builder.messageExpirationTime = copy.messageExpirationTime();
        builder.messageExpirationTimeVersion = copy.messageExpirationTimeVersion();
        builder.muteUntil = copy.muteUntil();
        builder.hideStory = copy.hideStory();
        builder.isBlocked = copy.isBlocked();
        builder.isArchived = copy.isArchived();
        builder.isProfileSharingEnabled = copy.isProfileSharingEnabled();
        builder.isHidden = copy.isHidden();
        builder.unregisteredTimestamp = copy.unregisteredTimestamp();
        return builder;
    }

    public String getName() {
        final var noGivenName = Util.isEmpty(givenName);
        final var noFamilyName = Util.isEmpty(familyName);

        if (noGivenName && noFamilyName) {
            return "";
        } else if (noGivenName) {
            return familyName;
        } else if (noFamilyName) {
            return givenName;
        }

        return givenName + " " + familyName;
    }

    public static final class Builder {

        private String givenName;
        private String familyName;
        private String nickName;
        private String nickNameGivenName;
        private String nickNameFamilyName;
        private String note;
        private String color;
        private int messageExpirationTime;
        private int messageExpirationTimeVersion = 1;
        private long muteUntil;
        private boolean hideStory;
        private boolean isBlocked;
        private boolean isArchived;
        private boolean isProfileSharingEnabled;
        private boolean isHidden;
        private Long unregisteredTimestamp;

        private Builder() {
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder withGivenName(final String val) {
            givenName = val;
            return this;
        }

        public Builder withFamilyName(final String val) {
            familyName = val;
            return this;
        }

        public Builder withNickName(final String val) {
            nickName = val;
            return this;
        }

        public Builder withNickNameGivenName(final String val) {
            nickNameGivenName = val;
            return this;
        }

        public Builder withNickNameFamilyName(final String val) {
            nickNameFamilyName = val;
            return this;
        }

        public Builder withNote(final String val) {
            note = val;
            return this;
        }

        public Builder withColor(final String val) {
            color = val;
            return this;
        }

        public Builder withMessageExpirationTime(final int val) {
            messageExpirationTime = val;
            return this;
        }

        public Builder withMessageExpirationTimeVersion(final int val) {
            messageExpirationTimeVersion = val;
            return this;
        }

        public Builder withMuteUntil(final long val) {
            muteUntil = val;
            return this;
        }

        public Builder withHideStory(final boolean val) {
            hideStory = val;
            return this;
        }

        public Builder withIsBlocked(final boolean val) {
            isBlocked = val;
            return this;
        }

        public Builder withIsArchived(final boolean val) {
            isArchived = val;
            return this;
        }

        public Builder withIsProfileSharingEnabled(final boolean val) {
            isProfileSharingEnabled = val;
            return this;
        }

        public Builder withIsHidden(final boolean val) {
            isHidden = val;
            return this;
        }

        public Builder withUnregisteredTimestamp(final Long val) {
            unregisteredTimestamp = val;
            return this;
        }

        public Contact build() {
            return new Contact(this);
        }
    }
}
