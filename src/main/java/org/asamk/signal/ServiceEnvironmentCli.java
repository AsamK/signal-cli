package org.asamk.signal;

public enum ServiceEnvironmentCli {
    LIVE {
        @Override
        public String toString() {
            return "live";
        }
    },
    SANDBOX {
        @Override
        public String toString() {
            return "sandbox";
        }
    },
}
