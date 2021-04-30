package org.asamk.signal.manager.storage.recipients;

import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class Recipient {

    private final RecipientId recipientId;

    private final SignalServiceAddress address;

    private final Contact contact;

    private final ProfileKey profileKey;

    private final ProfileKeyCredential profileKeyCredential;

    private final Profile profile;

    public Recipient(
            final RecipientId recipientId,
            final SignalServiceAddress address,
            final Contact contact,
            final ProfileKey profileKey,
            final ProfileKeyCredential profileKeyCredential,
            final Profile profile
    ) {
        this.recipientId = recipientId;
        this.address = address;
        this.contact = contact;
        this.profileKey = profileKey;
        this.profileKeyCredential = profileKeyCredential;
        this.profile = profile;
    }

    private Recipient(final Builder builder) {
        recipientId = builder.recipientId;
        address = builder.address;
        contact = builder.contact;
        profileKey = builder.profileKey;
        profileKeyCredential = builder.profileKeyCredential;
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
        builder.profileKeyCredential = copy.getProfileKeyCredential();
        builder.profile = copy.getProfile();
        return builder;
    }

    public RecipientId getRecipientId() {
        return recipientId;
    }

    public SignalServiceAddress getAddress() {
        return address;
    }

    public Contact getContact() {
        return contact;
    }

    public ProfileKey getProfileKey() {
        return profileKey;
    }

    public ProfileKeyCredential getProfileKeyCredential() {
        return profileKeyCredential;
    }

    public Profile getProfile() {
        return profile;
    }

    public static final class Builder {

        private RecipientId recipientId;
        private SignalServiceAddress address;
        private Contact contact;
        private ProfileKey profileKey;
        private ProfileKeyCredential profileKeyCredential;
        private Profile profile;

        private Builder() {
        }

        public Builder withRecipientId(final RecipientId val) {
            recipientId = val;
            return this;
        }

        public Builder withAddress(final SignalServiceAddress val) {
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

        public Builder withProfileKeyCredential(final ProfileKeyCredential val) {
            profileKeyCredential = val;
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
