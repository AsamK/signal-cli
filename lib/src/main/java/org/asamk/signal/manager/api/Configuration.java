package org.asamk.signal.manager.api;

import java.util.Optional;

public record Configuration(
        Optional<Boolean> readReceipts,
        Optional<Boolean> unidentifiedDeliveryIndicators,
        Optional<Boolean> typingIndicators,
        Optional<Boolean> linkPreviews
) {}
