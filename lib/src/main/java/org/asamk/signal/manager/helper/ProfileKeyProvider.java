package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.zkgroup.profiles.ProfileKey;

public interface ProfileKeyProvider {

    ProfileKey getProfileKey(RecipientId address);
}
