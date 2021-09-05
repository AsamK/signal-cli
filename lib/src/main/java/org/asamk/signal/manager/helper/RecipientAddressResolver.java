package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientId;

public interface RecipientAddressResolver {

    RecipientAddress resolveRecipientAddress(RecipientId recipientId);
}
