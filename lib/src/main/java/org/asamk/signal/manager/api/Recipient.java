package org.asamk.signal.manager.api;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;

import java.util.Objects;

public class Recipient {

    private final RecipientId recipientId;

    private final RecipientAddress address;

    private final Contact contact;

    private final ProfileKey profileKey;

    private final ExpiringProfileKeyCredential expiringProfileKeyCredential;

    private final Profile profile;

    public Recipient(
            final RecipientId recipientId,
            final RecipientAddress address,
            final Contact contact,
            final ProfileKey profileKey,
            final ExpiringProfileKeyCredential expiringProfileKeyCredential,
            final Profile profile
    ) {
        this.recipientId = recipientId;
        this.address = address;
        this.contact = contact;
        this.profileKey = profileKey;
        this.expiringProfileKeyCredential = expiringProfileKeyCredential;
        this.profile = profile;
    }

    private Recipient(final Builder builder) {
        recipientId = builder.recipientId;
        address = builder.address;
        contact = builder.contact;
        profileKey = builder.profileKey;
        expiringProfileKeyCredential = builder.expiringProfileKeyCredential1;
        profile = builder.profile;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(final Recipient copy) {
        Builder builder = new Builder();
        builder.recipientId = copy.getRecipientId();
        builder.address = copy.getAddress();
        builder.contact = copy.getContact();
        builder.profileKey = copy.getProfileKey();
        builder.expiringProfileKeyCredential1 = copy.getExpiringProfileKeyCredential();
        builder.profile = copy.getProfile();
        return builder;
    }

    public RecipientId getRecipientId() {
        return recipientId;
    }

    public RecipientAddress getAddress() {
        return address;
    }

    public Contact getContact() {
        return contact;
    }

    public ProfileKey getProfileKey() {
        return profileKey;
    }

    public ExpiringProfileKeyCredential getExpiringProfileKeyCredential() {
        return expiringProfileKeyCredential;
    }

    public Profile getProfile() {
        return profile;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Recipient recipient = (Recipient) o;
        return Objects.equals(recipientId, recipient.recipientId)
                && Objects.equals(address, recipient.address)
                && Objects.equals(contact, recipient.contact)
                && Objects.equals(profileKey, recipient.profileKey)
                && Objects.equals(expiringProfileKeyCredential, recipient.expiringProfileKeyCredential)
                && Objects.equals(profile, recipient.profile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipientId, address, contact, profileKey, expiringProfileKeyCredential, profile);
    }

    public static final class Builder {

        private RecipientId recipientId;
        private RecipientAddress address;
        private Contact contact;
        private ProfileKey profileKey;
        private ExpiringProfileKeyCredential expiringProfileKeyCredential1;
        private Profile profile;

        private Builder() {
        }

        public Builder withRecipientId(final RecipientId val) {
            recipientId = val;
            return this;
        }

        public Builder withAddress(final RecipientAddress val) {
            address = val;
            return this;
        }

        public Builder withContact(final Contact val) {
            contact = val;
            return this;
        }

        public Builder withProfileKey(final ProfileKey val) {
            profileKey = val;
            return this;
        }

        public Builder withExpiringProfileKeyCredential(final ExpiringProfileKeyCredential val) {
            expiringProfileKeyCredential1 = val;
            return this;
        }

        public Builder withProfile(final Profile val) {
            profile = val;
            return this;
        }

        public Recipient build() {
            return new Recipient(this);
        }
    }
}
