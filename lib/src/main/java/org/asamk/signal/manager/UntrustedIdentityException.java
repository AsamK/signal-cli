package org.asamk.signal.manager;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class UntrustedIdentityException extends Exception {

    private final SignalServiceAddress sender;
    private final Integer senderDevice;

    public UntrustedIdentityException(final SignalServiceAddress sender) {
        this(sender, null);
    }

    public UntrustedIdentityException(final SignalServiceAddress sender, final Integer senderDevice) {
        super("Untrusted identity: " + sender.getIdentifier());
        this.sender = sender;
        this.senderDevice = senderDevice;
    }

    public SignalServiceAddress getSender() {
        return sender;
    }

    public Integer getSenderDevice() {
        return senderDevice;
    }
}
