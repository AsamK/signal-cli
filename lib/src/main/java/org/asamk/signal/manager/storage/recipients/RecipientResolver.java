package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface RecipientResolver {

    RecipientId resolveRecipient(SignalServiceAddress address);
}
