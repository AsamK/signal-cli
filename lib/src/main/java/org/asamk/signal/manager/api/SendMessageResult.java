package org.asamk.signal.manager.api;

import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;

public record SendMessageResult(
        RecipientAddress address,
        boolean isSuccess,
        boolean isNetworkFailure,
        boolean isUnregisteredFailure,
        boolean isIdentityFailure,
        boolean isRateLimitFailure,
        ProofRequiredException proofRequiredFailure,
        boolean isInvalidPreKeyFailure,
        Long rateLimitRetryAfterSeconds
) {

    /**
     * Source-compatible constructor for callers built against the pre-retry-after record shape.
     * Delegates to the canonical constructor with a null retry-after, which is the correct value
     * for any result not produced by {@link #from}.
     */
    public SendMessageResult(
            RecipientAddress address,
            boolean isSuccess,
            boolean isNetworkFailure,
            boolean isUnregisteredFailure,
            boolean isIdentityFailure,
            boolean isRateLimitFailure,
            ProofRequiredException proofRequiredFailure,
            boolean isInvalidPreKeyFailure
    ) {
        this(address,
                isSuccess,
                isNetworkFailure,
                isUnregisteredFailure,
                isIdentityFailure,
                isRateLimitFailure,
                proofRequiredFailure,
                isInvalidPreKeyFailure,
                null);
    }

    public static SendMessageResult unregisteredFailure(RecipientAddress address) {
        return new SendMessageResult(address, false, false, true, false, false, null, false, null);
    }

    public static SendMessageResult from(
            final org.whispersystems.signalservice.api.messages.SendMessageResult sendMessageResult,
            RecipientResolver recipientResolver,
            RecipientAddressResolver addressResolver
    ) {
        final var rateLimitFailure = sendMessageResult.getRateLimitFailure();
        final var proofRequiredFailure = sendMessageResult.getProofRequiredFailure();
        final Long retryAfterSeconds;
        if (proofRequiredFailure != null) {
            retryAfterSeconds = proofRequiredFailure.getRetryAfterSeconds();
        } else if (rateLimitFailure != null) {
            retryAfterSeconds = rateLimitFailure.getRetryAfterMilliseconds()
                    .map(SendMessageResult::millisToCeilingSeconds)
                    .orElse(null);
        } else {
            retryAfterSeconds = null;
        }
        return new SendMessageResult(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(
                sendMessageResult.getAddress())).toApiRecipientAddress(),
                sendMessageResult.isSuccess(),
                sendMessageResult.isNetworkFailure(),
                sendMessageResult.isUnregisteredFailure(),
                sendMessageResult.getIdentityFailure() != null,
                rateLimitFailure != null || proofRequiredFailure != null,
                proofRequiredFailure == null ? null : new ProofRequiredException(proofRequiredFailure),
                sendMessageResult.isInvalidPreKeyFailure(),
                retryAfterSeconds);
    }

    static long millisToCeilingSeconds(long millis) {
        // Round up so we never advise a retry before the server's deadline.
        return (millis + 999L) / 1000L;
    }
}
