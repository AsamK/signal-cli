package org.asamk.signal.commands;

enum MessageRequestResponseType {
    ACCEPT {
        @Override
        public String toString() {
            return "accept";
        }
    },
    DELETE {
        @Override
        public String toString() {
            return "delete";
        }
    }
}
