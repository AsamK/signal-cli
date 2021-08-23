package org.asamk.signal;

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
    },
}
