package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface RecipientTrustedResolver {

    RecipientId resolveSelfRecipientTrusted(RecipientAddress address);

    RecipientId resolveRecipientTrusted(SignalServiceAddress address);
}
