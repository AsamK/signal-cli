package org.asamk.signal.manager.storage.recipients;

import org.signal.libsignal.zkgroup.profiles.ProfileKey;

public interface SelfProfileKeyProvider {

    ProfileKey getSelfProfileKey();
}
