package org.asamk.signal.manager.api;

public class CaptchaRequiredException extends Exception {

    private long nextAttemptTimestamp;

    public CaptchaRequiredException(final long nextAttemptTimestamp) {
        super("Captcha required");
        this.nextAttemptTimestamp = nextAttemptTimestamp;
    }

    public CaptchaRequiredException(final String message) {
        super(message);
    }

    public CaptchaRequiredException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public long getNextAttemptTimestamp() {
        return nextAttemptTimestamp;
    }
}
