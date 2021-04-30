package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;

public interface ProfileProvider {

    Profile getProfile(RecipientId address);
}
