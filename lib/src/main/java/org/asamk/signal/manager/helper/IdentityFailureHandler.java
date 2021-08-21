package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.whispersystems.signalservice.api.messages.SendMessageResult;

public interface IdentityFailureHandler {

    void handleIdentityFailure(RecipientId recipientId, SendMessageResult.IdentityFailure identityFailure);
}
