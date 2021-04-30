package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface SignalServiceAddressResolver {

    SignalServiceAddress resolveSignalServiceAddress(RecipientId recipientId);
}
