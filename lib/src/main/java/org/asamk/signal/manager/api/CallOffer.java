package org.asamk.signal.manager.api;

public record CallOffer(
        long callId,
        Type type,
        byte[] opaque
) {

    public enum Type {
        AUDIO,
        VIDEO
    }
}
