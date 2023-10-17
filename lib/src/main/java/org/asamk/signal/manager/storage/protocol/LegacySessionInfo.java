package org.asamk.signal.manager.storage.protocol;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;

public class LegacySessionInfo {

    public final RecipientAddress address;

    public final int deviceId;

    public final byte[] sessionRecord;

    LegacySessionInfo(final RecipientAddress address, final int deviceId, final byte[] sessionRecord) {
        this.address = address;
        this.deviceId = deviceId;
        this.sessionRecord = sessionRecord;
    }
}
