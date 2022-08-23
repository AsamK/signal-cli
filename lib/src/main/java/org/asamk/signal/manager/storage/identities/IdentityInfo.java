package org.asamk.signal.manager.storage.identities;

import org.asamk.signal.manager.api.TrustLevel;
import org.signal.libsignal.protocol.IdentityKey;
import org.whispersystems.signalservice.api.push.ServiceId;

public class IdentityInfo {

    private final ServiceId serviceId;
    private final IdentityKey identityKey;
    private final TrustLevel trustLevel;
    private final long addedTimestamp;

    IdentityInfo(
            final ServiceId serviceId, IdentityKey identityKey, TrustLevel trustLevel, long addedTimestamp
    ) {
        this.serviceId = serviceId;
        this.identityKey = identityKey;
        this.trustLevel = trustLevel;
        this.addedTimestamp = addedTimestamp;
    }

    public ServiceId getServiceId() {
        return serviceId;
    }

    public IdentityKey getIdentityKey() {
        return this.identityKey;
    }

    public TrustLevel getTrustLevel() {
        return this.trustLevel;
    }

    boolean isTrusted() {
        return trustLevel == TrustLevel.TRUSTED_UNVERIFIED || trustLevel == TrustLevel.TRUSTED_VERIFIED;
    }

    public long getDateAddedTimestamp() {
        return this.addedTimestamp;
    }
}
