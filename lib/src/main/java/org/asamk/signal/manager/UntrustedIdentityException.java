package org.asamk.signal.manager;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;

public class UntrustedIdentityException extends Exception {

    private final RecipientAddress sender;
    private final Integer senderDevice;

    public UntrustedIdentityException(final RecipientAddress sender) {
        this(sender, null);
    }

    public UntrustedIdentityException(final RecipientAddress sender, final Integer senderDevice) {
        super("Untrusted identity: " + sender.getIdentifier());
        this.sender = sender;
        this.senderDevice = senderDevice;
    }

    public RecipientAddress getSender() {
        return sender;
    }

    public Integer getSenderDevice() {
        return senderDevice;
    }
}
