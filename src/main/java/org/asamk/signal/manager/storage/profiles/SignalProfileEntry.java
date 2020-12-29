package org.asamk.signal.manager.storage.profiles;

import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SignalProfileEntry {

    private final SignalServiceAddress serviceAddress;

    private final ProfileKey profileKey;

    private final long lastUpdateTimestamp;

    private final SignalProfile profile;

    private final ProfileKeyCredential profileKeyCredential;

    private boolean requestPending;

    public SignalProfileEntry(
            final SignalServiceAddress serviceAddress,
            final ProfileKey profileKey,
            final long lastUpdateTimestamp,
            final SignalProfile profile,
            final ProfileKeyCredential profileKeyCredential
    ) {
        this.serviceAddress = serviceAddress;
        this.profileKey = profileKey;
        this.lastUpdateTimestamp = lastUpdateTimestamp;
        this.profile = profile;
        this.profileKeyCredential = profileKeyCredential;
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

    public ProfileKeyCredential getProfileKeyCredential() {
        return profileKeyCredential;
    }

    public boolean isRequestPending() {
        return requestPending;
    }

    public void setRequestPending(final boolean requestPending) {
        this.requestPending = requestPending;
    }
}
