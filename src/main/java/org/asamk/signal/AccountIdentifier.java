package org.asamk.signal;

import java.util.Locale;
import java.util.UUID;

public final class AccountIdentifier {

    private AccountIdentifier() {
    }

    public static String normalize(final String identifier) {
        if (identifier == null) {
            return null;
        }

        final var compact = identifier.replace("-", "").replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        if (compact.length() != 32 || !compact.matches("[0-9a-f]{32}")) {
            return identifier;
        }

        final var uuid = "%s-%s-%s-%s-%s".formatted(compact.substring(0, 8),
                compact.substring(8, 12),
                compact.substring(12, 16),
                compact.substring(16, 20),
                compact.substring(20));
        try {
            return UUID.fromString(uuid).toString();
        } catch (IllegalArgumentException e) {
            return identifier;
        }
    }
}
