package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;

public enum TypingAction {
    START,
    STOP;

    public SignalServiceTypingMessage.Action toSignalService() {
        switch (this) {
            case START:
                return SignalServiceTypingMessage.Action.STARTED;
            case STOP:
                return SignalServiceTypingMessage.Action.STOPPED;
            default:
                throw new IllegalStateException("Invalid typing action " + this);
        }
    }
}
