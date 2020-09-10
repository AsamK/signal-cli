package org.asamk.signal.storage.profiles;

import org.signal.zkgroup.profiles.ProfileKey;

public class SignalProfileEntry {

    private ProfileKey profileKey;

    private long lastUpdateTimestamp;

    private SignalProfile profile;

    public SignalProfileEntry(final ProfileKey profileKey, final long lastUpdateTimestamp, final SignalProfile profile) {
        this.profileKey = profileKey;
        this.lastUpdateTimestamp = lastUpdateTimestamp;
        this.profile = profile;
    }

    public ProfileKey getProfileKey() {
        return profileKey;
    }

    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    public SignalProfile getProfile() {
        return profile;
    }
}
