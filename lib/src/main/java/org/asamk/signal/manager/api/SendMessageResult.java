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
        return new SendMessageResult(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(
                sendMessageResult.getAddress())).toApiRecipientAddress(),
                sendMessageResult.isSuccess(),
                sendMessageResult.isNetworkFailure(),
                sendMessageResult.isUnregisteredFailure(),
                sendMessageResult.getIdentityFailure() != null,
                rateLimitFailure != null || proofRequiredFailure != null,
                proofRequiredFailure == null ? null : new ProofRequiredException(proofRequiredFailure),
                sendMessageResult.isInvalidPreKeyFailure(),
                rateLimitFailure == null
                        ? null
                        : rateLimitFailure.getRetryAfterMilliseconds().map(ms -> ms / 1000L).orElse(null));
    }
}
