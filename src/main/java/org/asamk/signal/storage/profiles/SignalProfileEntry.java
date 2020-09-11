package org.asamk.signal.storage.profiles;

import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SignalProfileEntry {

    private final SignalServiceAddress serviceAddress;

    private final ProfileKey profileKey;

    private final long lastUpdateTimestamp;

    private final SignalProfile profile;

    public SignalProfileEntry(final SignalServiceAddress serviceAddress, final ProfileKey profileKey, final long lastUpdateTimestamp, final SignalProfile profile) {
        this.serviceAddress = serviceAddress;
        this.profileKey = profileKey;
        this.lastUpdateTimestamp = lastUpdateTimestamp;
        this.profile = profile;
    }

    public SignalServiceAddress getServiceAddress() {
        return serviceAddress;
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
