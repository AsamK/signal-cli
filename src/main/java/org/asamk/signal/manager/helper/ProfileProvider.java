package org.asamk.signal.manager.helper;

import org.asamk.signal.storage.profiles.SignalProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface ProfileProvider {

    SignalProfile getProfile(SignalServiceAddress address);
}
