package org.asamk.signal.manager.helper;

import org.whispersystems.signalservice.api.services.ProfileService;

public interface ProfileServiceProvider {

    ProfileService getProfileService();
}
