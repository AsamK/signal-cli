package org.asamk.signal.commands;

enum ReceiveMode {
    ON_START {
        @Override
        public String toString() {
            return "on-start";
        }
    },
    ON_CONNECTION {
        @Override
        public String toString() {
            return "on-connection";
        }
    },
    MANUAL {
        @Override
        public String toString() {
            return "manual";
        }
    },
}
