package org.asamk.signal;

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
    },
}
