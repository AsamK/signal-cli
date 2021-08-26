package org.asamk.signal.manager.storage.profiles;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;

public class LegacySignalProfileEntry {

    private final RecipientAddress address;

    private final ProfileKey profileKey;

    private final long lastUpdateTimestamp;

    private final SignalProfile profile;

    private final ProfileKeyCredential profileKeyCredential;

    public LegacySignalProfileEntry(
            final RecipientAddress address,
            final ProfileKey profileKey,
            final long lastUpdateTimestamp,
            final SignalProfile profile,
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

    public SignalProfile getProfile() {
        return profile;
    }

    public ProfileKeyCredential getProfileKeyCredential() {
        return profileKeyCredential;
    }
}
