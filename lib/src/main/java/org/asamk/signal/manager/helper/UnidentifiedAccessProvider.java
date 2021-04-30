package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;

public interface UnidentifiedAccessProvider {

    Optional<UnidentifiedAccessPair> getAccessFor(RecipientId recipientId);
}
