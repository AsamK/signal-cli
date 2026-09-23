package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.SendMessageResult;

import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(title = "SendMessageResult")
public record JsonSendMessageResult(
        JsonRecipientAddress recipientAddress,
        @JsonInclude(JsonInclude.Include.NON_NULL) String groupId,
        Type type,
        @JsonInclude(JsonInclude.Include.NON_NULL) String token,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long retryAfterSeconds
) {

    public static JsonSendMessageResult from(SendMessageResult result) {
        return from(result, null);
    }

    public static JsonSendMessageResult from(SendMessageResult result, GroupId groupId) {
        final var rateLimitRetryAfterMilliseconds = result.rateLimitRetryAfterMilliseconds();
        return new JsonSendMessageResult(JsonRecipientAddress.from(result.address()),
                groupId != null ? groupId.toBase64() : null,
                result.isSuccess()
                        ? Type.SUCCESS
                        : result.isRateLimitFailure()
                                ? Type.RATE_LIMIT_FAILURE
                                : result.isNetworkFailure()
                                        ? Type.NETWORK_FAILURE
                                        : result.isUnregisteredFailure()
                                                ? Type.UNREGISTERED_FAILURE
                                                : result.isInvalidPreKeyFailure()
                                                        ? Type.INVALID_PRE_KEY_FAILURE
                                                        : Type.IDENTITY_FAILURE,
                result.proofRequiredFailure() != null ? result.proofRequiredFailure().getToken() : null,
                rateLimitRetryAfterMilliseconds == null ? null : Math.ceilDiv(rateLimitRetryAfterMilliseconds, 1000L));
    }

    public enum Type {
        SUCCESS,
        NETWORK_FAILURE,
        UNREGISTERED_FAILURE,
        IDENTITY_FAILURE,
        RATE_LIMIT_FAILURE,
        INVALID_PRE_KEY_FAILURE,
    }
}
