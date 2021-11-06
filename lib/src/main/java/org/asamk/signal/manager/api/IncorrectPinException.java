package org.asamk.signal.manager.api;

public class IncorrectPinException extends Exception {

    private final int triesRemaining;

    public IncorrectPinException(int triesRemaining) {
        this.triesRemaining = triesRemaining;
    }

    public int getTriesRemaining() {
        return triesRemaining;
    }
}
