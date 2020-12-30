package org.asamk.signal.manager.helper;

import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface ProfileKeyCredentialProvider {

    ProfileKeyCredential getProfileKeyCredential(SignalServiceAddress address);
}
