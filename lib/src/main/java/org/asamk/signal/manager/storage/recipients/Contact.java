package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.internal.util.Util;

import java.util.Objects;

public class Contact {

    private final String givenName;

    private final String familyName;

    private final String color;

    private final int messageExpirationTime;

    private final boolean blocked;

    private final boolean archived;

    private final boolean profileSharingEnabled;

    public Contact(
            final String givenName,
            final String familyName,
            final String color,
            final int messageExpirationTime,
            final boolean blocked,
            final boolean archived,
            final boolean profileSharingEnabled
    ) {
        this.givenName = givenName;
        this.familyName = familyName;
        this.color = color;
        this.messageExpirationTime = messageExpirationTime;
        this.blocked = blocked;
        this.archived = archived;
        this.profileSharingEnabled = profileSharingEnabled;
    }

    private Contact(final Builder builder) {
        givenName = builder.givenName;
        familyName = builder.familyName;
        color = builder.color;
        messageExpirationTime = builder.messageExpirationTime;
        blocked = builder.blocked;
        archived = builder.archived;
        profileSharingEnabled = builder.profileSharingEnabled;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(final Contact copy) {
        Builder builder = new Builder();
        builder.givenName = copy.getGivenName();
        builder.familyName = copy.getFamilyName();
        builder.color = copy.getColor();
        builder.messageExpirationTime = copy.getMessageExpirationTime();
        builder.blocked = copy.isBlocked();
        builder.archived = copy.isArchived();
        builder.profileSharingEnabled = copy.isProfileSharingEnabled();
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

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public String getColor() {
        return color;
    }

    public int getMessageExpirationTime() {
        return messageExpirationTime;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isArchived() {
        return archived;
    }

    public boolean isProfileSharingEnabled() {
        return profileSharingEnabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Contact contact = (Contact) o;
        return messageExpirationTime == contact.messageExpirationTime
                && blocked == contact.blocked
                && archived == contact.archived
                && profileSharingEnabled == contact.profileSharingEnabled
                && Objects.equals(givenName, contact.givenName)
                && Objects.equals(familyName, contact.familyName)
                && Objects.equals(color, contact.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(givenName,
                familyName,
                color,
                messageExpirationTime,
                blocked,
                archived,
                profileSharingEnabled);
    }

    public static final class Builder {

        private String givenName;
        private String familyName;
        private String color;
        private int messageExpirationTime;
        private boolean blocked;
        private boolean archived;
        private boolean profileSharingEnabled;

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

        public Builder withColor(final String val) {
            color = val;
            return this;
        }

        public Builder withMessageExpirationTime(final int val) {
            messageExpirationTime = val;
            return this;
        }

        public Builder withBlocked(final boolean val) {
            blocked = val;
            return this;
        }

        public Builder withArchived(final boolean val) {
            archived = val;
            return this;
        }

        public Builder withProfileSharingEnabled(final boolean val) {
            profileSharingEnabled = val;
            return this;
        }

        public Contact build() {
            return new Contact(this);
        }
    }
}
