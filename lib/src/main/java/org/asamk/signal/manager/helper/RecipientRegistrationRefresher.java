package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.storage.recipients.RecipientId;

import java.io.IOException;

public interface RecipientRegistrationRefresher {

    RecipientId refreshRecipientRegistration(RecipientId recipientId) throws IOException, UnregisteredRecipientException;
}
