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
        boolean isInvalidPreKeyFailure
) {

    public static SendMessageResult unregisteredFailure(RecipientAddress address) {
        return new SendMessageResult(address, false, false, true, false, false, null, false);
    }

    public static SendMessageResult from(
            final org.whispersystems.signalservice.api.messages.SendMessageResult sendMessageResult,
            RecipientResolver recipientResolver,
            RecipientAddressResolver addressResolver
    ) {
        return new SendMessageResult(addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(
                sendMessageResult.getAddress())).toApiRecipientAddress(),
                sendMessageResult.isSuccess(),
                sendMessageResult.isNetworkFailure(),
                sendMessageResult.isUnregisteredFailure(),
                sendMessageResult.getIdentityFailure() != null,
                sendMessageResult.getRateLimitFailure() != null || sendMessageResult.getProofRequiredFailure() != null,
                sendMessageResult.getProofRequiredFailure() == null
                        ? null
                        : new ProofRequiredException(sendMessageResult.getProofRequiredFailure()),
                sendMessageResult.isInvalidPreKeyFailure());
    }
}
