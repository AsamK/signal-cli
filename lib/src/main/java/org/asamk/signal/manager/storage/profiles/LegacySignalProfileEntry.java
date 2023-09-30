package org.asamk.signal.manager.storage.profiles;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;

public record LegacySignalProfileEntry(
        RecipientAddress address, ProfileKey profileKey, long lastUpdateTimestamp, LegacySignalProfile profile
) {}
