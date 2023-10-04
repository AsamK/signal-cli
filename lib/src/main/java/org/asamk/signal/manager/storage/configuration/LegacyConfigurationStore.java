package org.asamk.signal.manager.storage.configuration;

import org.asamk.signal.manager.api.PhoneNumberSharingMode;

public class LegacyConfigurationStore {

    public static void migrate(Storage storage, ConfigurationStore configurationStore) {
        if (storage.readReceipts != null) {
            configurationStore.setReadReceipts(storage.readReceipts);
        }
        if (storage.unidentifiedDeliveryIndicators != null) {
            configurationStore.setUnidentifiedDeliveryIndicators(storage.unidentifiedDeliveryIndicators);
        }
        if (storage.typingIndicators != null) {
            configurationStore.setTypingIndicators(storage.typingIndicators);
        }
        if (storage.linkPreviews != null) {
            configurationStore.setLinkPreviews(storage.linkPreviews);
        }
        if (storage.phoneNumberSharingMode != null) {
            configurationStore.setPhoneNumberSharingMode(storage.phoneNumberSharingMode);
        }
    }

    public record Storage(
            Boolean readReceipts,
            Boolean unidentifiedDeliveryIndicators,
            Boolean typingIndicators,
            Boolean linkPreviews,
            Boolean phoneNumberUnlisted,
            PhoneNumberSharingMode phoneNumberSharingMode
    ) {}
}
