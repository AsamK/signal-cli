package org.asamk.signal;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TrustNewIdentityCli {
    ALWAYS {
        @Override
        public String toString() {
            return "always";
        }
    },
    ON_FIRST_USE {
        @Override
        public String toString() {
            return "on-first-use";
        }
    },
    NEVER {
        @Override
        public String toString() {
            return "never";
        }
    };

    @JsonCreator
    public static TrustNewIdentityCli fromString(String value) {
        if (value == null) return null;
        final var norm = value.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        return switch (norm) {
            case "always" -> ALWAYS;
            case "onfirstuse" -> ON_FIRST_USE;
            case "never" -> NEVER;
            default -> throw new IllegalArgumentException("Invalid trust-new-identities: " + value);
        };
    }
}
