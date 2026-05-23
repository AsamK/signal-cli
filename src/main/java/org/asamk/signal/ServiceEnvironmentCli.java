package org.asamk.signal;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ServiceEnvironmentCli {
    LIVE {
        @Override
        public String toString() {
            return "live";
        }
    },
    STAGING {
        @Override
        public String toString() {
            return "staging";
        }
    };

    @JsonCreator
    public static ServiceEnvironmentCli fromString(String value) {
        if (value == null) return null;
        final var norm = value.trim().toLowerCase();
        return switch (norm) {
            case "live" -> LIVE;
            case "staging" -> STAGING;
            default -> throw new IllegalArgumentException("Invalid service-environment: " + value);
        };
    }
}
