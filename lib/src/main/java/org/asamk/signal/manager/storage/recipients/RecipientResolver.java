package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface RecipientResolver {

    RecipientId resolveRecipient(String identifier);

    RecipientId resolveRecipient(RecipientAddress address);

    RecipientId resolveRecipient(SignalServiceAddress address);

    RecipientId resolveRecipient(ServiceId aci);

    RecipientId resolveRecipient(long recipientId);
}
