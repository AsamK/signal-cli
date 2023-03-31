package org.asamk.signal.manager.api;

public class RateLimitException extends Exception {

    private final long nextAttemptTimestamp;

    public RateLimitException(final long nextAttemptTimestamp) {
        super("Rate limit");
        this.nextAttemptTimestamp = nextAttemptTimestamp;
    }

    public long getNextAttemptTimestamp() {
        return nextAttemptTimestamp;
    }
}
