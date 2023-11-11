package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.internal.util.Util;

public record Contact(
        String givenName,
        String familyName,
        String color,
        int messageExpirationTime,
        boolean isBlocked,
        boolean isArchived,
        boolean isProfileSharingEnabled,
        boolean isHidden
) {

    private Contact(final Builder builder) {
        this(builder.givenName,
                builder.familyName,
                builder.color,
                builder.messageExpirationTime,
                builder.isBlocked,
                builder.isArchived,
                builder.isProfileSharingEnabled,
                builder.isHidden);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(final Contact copy) {
        Builder builder = new Builder();
        builder.givenName = copy.givenName();
        builder.familyName = copy.familyName();
        builder.color = copy.color();
        builder.messageExpirationTime = copy.messageExpirationTime();
        builder.isBlocked = copy.isBlocked();
        builder.isArchived = copy.isArchived();
        builder.isProfileSharingEnabled = copy.isProfileSharingEnabled();
        builder.isHidden = copy.isHidden();
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
        private String color;
        private int messageExpirationTime;
        private boolean isBlocked;
        private boolean isArchived;
        private boolean isProfileSharingEnabled;
        private boolean isHidden;

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

        public Builder withColor(final String val) {
            color = val;
            return this;
        }

        public Builder withMessageExpirationTime(final int val) {
            messageExpirationTime = val;
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

        public Contact build() {
            return new Contact(this);
        }
    }
}
