package org.asamk.signal.manager.storage.profiles;

import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;

public interface ProfileStore {

    Profile getProfile(RecipientId recipientId);

    ProfileKey getProfileKey(RecipientId recipientId);

    ProfileKeyCredential getProfileKeyCredential(RecipientId recipientId);

    void storeProfile(RecipientId recipientId, Profile profile);

    void storeSelfProfileKey(RecipientId recipientId, ProfileKey profileKey);

    void storeProfileKey(RecipientId recipientId, ProfileKey profileKey);

    void storeProfileKeyCredential(RecipientId recipientId, ProfileKeyCredential profileKeyCredential);
}
