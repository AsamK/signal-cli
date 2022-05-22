package org.asamk.signal.manager.storage.recipients;

import org.asamk.signal.manager.storage.Utils;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface RecipientResolver {

    RecipientId resolveRecipient(RecipientAddress address);

    RecipientId resolveRecipient(long recipientId);

    default RecipientId resolveRecipient(String identifier) {
        return resolveRecipient(Utils.getRecipientAddressFromIdentifier(identifier));
    }

    default RecipientId resolveRecipient(SignalServiceAddress address) {
        return resolveRecipient(new RecipientAddress(address));
    }

    default RecipientId resolveRecipient(ServiceId serviceId) {
        return resolveRecipient(new RecipientAddress(serviceId.uuid()));
    }
}
