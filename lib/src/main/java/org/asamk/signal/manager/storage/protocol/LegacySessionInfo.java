package org.asamk.signal.manager.storage.protocol;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class LegacySessionInfo {

    public SignalServiceAddress address;

    public int deviceId;

    public byte[] sessionRecord;

    LegacySessionInfo(final SignalServiceAddress address, final int deviceId, final byte[] sessionRecord) {
        this.address = address;
        this.deviceId = deviceId;
        this.sessionRecord = sessionRecord;
    }
}
