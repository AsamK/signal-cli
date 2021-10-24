package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;

public enum TypingAction {
    START,
    STOP;

    public SignalServiceTypingMessage.Action toSignalService() {
        return switch (this) {
            case START -> SignalServiceTypingMessage.Action.STARTED;
            case STOP -> SignalServiceTypingMessage.Action.STOPPED;
        };
    }
}
