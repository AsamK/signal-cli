package org.asamk.signal.manager.api;

public class UntrustedIdentityException extends Exception {

    private final RecipientAddress sender;
    private final int senderDevice;

    public UntrustedIdentityException(final RecipientAddress sender, final int senderDevice) {
        super("Untrusted identity: " + sender.getIdentifier());
        this.sender = sender;
        this.senderDevice = senderDevice;
    }

    public RecipientAddress getSender() {
        return sender;
    }

    public int getSenderDevice() {
        return senderDevice;
    }
}
