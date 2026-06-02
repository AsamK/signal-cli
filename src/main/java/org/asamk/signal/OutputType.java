package org.asamk.signal;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OutputType {
    PLAIN_TEXT {
        @Override
        public String toString() {
            return "plain-text";
        }
    },
    JSON {
        @Override
        public String toString() {
            return "json";
        }
    };

    @JsonCreator
    public static OutputType fromString(String value) {
        if (value == null) return null;
        final var norm = value.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        return switch (norm) {
            case "plaintext" -> PLAIN_TEXT;
            case "json" -> JSON;
            default -> throw new IllegalArgumentException("Invalid output type: " + value);
        };
    }
}
