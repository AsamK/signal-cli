package org.asamk.signal.manager.api;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;

public class UnregisteredRecipientException extends Exception {

    private final RecipientAddress sender;

    public UnregisteredRecipientException(final RecipientAddress sender) {
        super("Unregistered user: " + sender.getIdentifier());
        this.sender = sender;
    }

    public RecipientAddress getSender() {
        return sender;
    }
}
