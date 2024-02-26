package org.asamk.signal.manager.api;

public class CaptchaRejectedException extends Exception {

    public CaptchaRejectedException() {
        super("Captcha rejected");
    }

    public CaptchaRejectedException(final String message) {
        super(message);
    }

    public CaptchaRejectedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
