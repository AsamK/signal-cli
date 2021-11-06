package org.asamk.signal.manager.api;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thrown when rate-limited by the server and proof of humanity is required to continue messaging.
 */
public class ProofRequiredException extends Exception {

    private final String token;
    private final Set<Option> options;
    private final long retryAfterSeconds;

    public ProofRequiredException(org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException e) {
        this.token = e.getToken();
        this.options = e.getOptions().stream().map(Option::from).collect(Collectors.toSet());
        this.retryAfterSeconds = e.getRetryAfterSeconds();
    }

    public String getToken() {
        return token;
    }

    public Set<Option> getOptions() {
        return options;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public enum Option {
        RECAPTCHA,
        PUSH_CHALLENGE;

        static Option from(org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException.Option option) {
            return switch (option) {
                case RECAPTCHA -> RECAPTCHA;
                case PUSH_CHALLENGE -> PUSH_CHALLENGE;
            };
        }
    }
}
