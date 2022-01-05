package org.asamk.signal;

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
    },
    @Deprecated SANDBOX {
        @Override
        public String toString() {
            return "sandbox";
        }
    },
}
