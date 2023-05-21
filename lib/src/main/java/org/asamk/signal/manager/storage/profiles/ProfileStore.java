package org.asamk.signal.manager.storage.profiles;

import org.asamk.signal.manager.api.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;

public interface ProfileStore {

    Profile getProfile(RecipientId recipientId);

    ProfileKey getProfileKey(RecipientId recipientId);

    ExpiringProfileKeyCredential getExpiringProfileKeyCredential(RecipientId recipientId);

    void storeProfile(RecipientId recipientId, Profile profile);

    void storeSelfProfileKey(RecipientId recipientId, ProfileKey profileKey);

    void storeProfileKey(RecipientId recipientId, ProfileKey profileKey);

    void storeExpiringProfileKeyCredential(
            RecipientId recipientId, ExpiringProfileKeyCredential expiringProfileKeyCredential
    );
}
