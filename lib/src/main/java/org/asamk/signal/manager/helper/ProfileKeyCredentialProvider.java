package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.zkgroup.profiles.ProfileKeyCredential;

public interface ProfileKeyCredentialProvider {

    ProfileKeyCredential getProfileKeyCredential(RecipientId recipientId);
}
