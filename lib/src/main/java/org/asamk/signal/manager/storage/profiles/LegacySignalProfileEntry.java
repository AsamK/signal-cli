package org.asamk.signal.manager.storage.profiles;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;

public class LegacySignalProfileEntry {

    private final RecipientAddress address;

    private final ProfileKey profileKey;

    private final long lastUpdateTimestamp;

    private final LegacySignalProfile profile;

    private final ProfileKeyCredential profileKeyCredential;

    public LegacySignalProfileEntry(
            final RecipientAddress address,
            final ProfileKey profileKey,
            final long lastUpdateTimestamp,
            final LegacySignalProfile profile,
            final ProfileKeyCredential profileKeyCredential
    ) {
        this.address = address;
        this.profileKey = profileKey;
        this.lastUpdateTimestamp = lastUpdateTimestamp;
        this.profile = profile;
        this.profileKeyCredential = profileKeyCredential;
    }

    public RecipientAddress getAddress() {
        return address;
    }

    public ProfileKey getProfileKey() {
        return profileKey;
    }

    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    public LegacySignalProfile getProfile() {
        return profile;
    }

    public ProfileKeyCredential getProfileKeyCredential() {
        return profileKeyCredential;
    }
}
