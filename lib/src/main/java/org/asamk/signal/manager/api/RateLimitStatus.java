package org.asamk.signal.manager.api;

/**
 * Snapshot of the most recent rate-limit state observed from send results.
 *
 * <p>{@code active} is true while the current system time is still inside the retry-after
 * window. When the window elapses, callers see {@code active = false} without needing to
 * clear the state themselves.
 *
 * <p>{@code proofRequired} distinguishes a plain HTTP 413 rate limit (resolved by waiting)
 * from a HTTP 428 challenge (requires captcha submission via
 * {@code submitRateLimitChallenge}). When {@code proofRequired} is true,
 * {@code challengeToken} is populated.
 */
public record RateLimitStatus(
        boolean active,
        boolean proofRequired,
        Long retryAfterSeconds,
        String challengeToken,
        Long expiresAtEpochSeconds
) {

    public static RateLimitStatus inactive() {
        return new RateLimitStatus(false, false, null, null, null);
    }
}
