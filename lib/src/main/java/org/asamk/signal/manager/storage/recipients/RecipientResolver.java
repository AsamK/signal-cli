package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.UUID;

public interface RecipientResolver {

    RecipientId resolveRecipient(String identifier);

    RecipientId resolveRecipient(RecipientAddress address);

    RecipientId resolveRecipient(SignalServiceAddress address);

    RecipientId resolveRecipient(UUID uuid);
}
