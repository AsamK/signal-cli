package org.asamk.signal.manager.helper;

import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface ProfileKeyProvider {

    ProfileKey getProfileKey(SignalServiceAddress address);
}
