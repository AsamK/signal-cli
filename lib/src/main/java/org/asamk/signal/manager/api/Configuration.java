package org.asamk.signal.manager.api;

import org.asamk.signal.manager.storage.configuration.ConfigurationStore;

import java.util.Optional;

public record Configuration(
        Optional<Boolean> readReceipts,
        Optional<Boolean> unidentifiedDeliveryIndicators,
        Optional<Boolean> typingIndicators,
        Optional<Boolean> linkPreviews
) {

    public static Configuration from(final ConfigurationStore configurationStore) {
        return new Configuration(Optional.ofNullable(configurationStore.getReadReceipts()),
                Optional.ofNullable(configurationStore.getUnidentifiedDeliveryIndicators()),
                Optional.ofNullable(configurationStore.getTypingIndicators()),
                Optional.ofNullable(configurationStore.getLinkPreviews()));
    }
}
