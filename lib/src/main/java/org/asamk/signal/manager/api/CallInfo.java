package org.asamk.signal.manager.api;

public record CallInfo(
        long callId,
        State state,
        RecipientAddress recipient,
        String inputDeviceName,
        String outputDeviceName,
        boolean isOutgoing
) {

    public enum State {
        IDLE,
        RINGING_INCOMING,
        RINGING_OUTGOING,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        ENDED
    }
}
