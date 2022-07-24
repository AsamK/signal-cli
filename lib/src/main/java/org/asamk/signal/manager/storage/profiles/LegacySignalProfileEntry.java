package org.asamk.signal.manager.storage.profiles;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;

public class LegacySignalProfileEntry {

    private final RecipientAddress address;

    private final ProfileKey profileKey;

    private final long lastUpdateTimestamp;

    private final LegacySignalProfile profile;

    public LegacySignalProfileEntry(
            final RecipientAddress address,
            final ProfileKey profileKey,
            final long lastUpdateTimestamp,
            final LegacySignalProfile profile
    ) {
        this.address = address;
        this.profileKey = profileKey;
        this.lastUpdateTimestamp = lastUpdateTimestamp;
        this.profile = profile;
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
}
