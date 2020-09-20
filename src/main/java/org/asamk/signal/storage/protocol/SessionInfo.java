package org.asamk.signal.storage.protocol;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SessionInfo {

    public SignalServiceAddress address;

    public int deviceId;

    public byte[] sessionRecord;

    public SessionInfo(final SignalServiceAddress address, final int deviceId, final byte[] sessionRecord) {
        this.address = address;
        this.deviceId = deviceId;
        this.sessionRecord = sessionRecord;
    }
}
