package org.asamk.signal.manager.api;

public class PinLockedException extends Exception {

    private final long timeRemaining;

    public PinLockedException(long timeRemaining) {
        this.timeRemaining = timeRemaining;
    }

    public long getTimeRemaining() {
        return timeRemaining;
    }
}
