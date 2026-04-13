package org.asamk.signal.json;

import org.asamk.signal.manager.api.RecipientAddress;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendMessageResult;
import org.asamk.signal.manager.api.SendMessageResults;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonSendMessageResultTest {

    private static final RecipientAddress ADDRESS = new RecipientAddress(null, null, "+15551234567", null);

    @Test
    void rateLimitFailureSurfacesRetryAfterSeconds() {
        var result = new SendMessageResult(ADDRESS,
                false, false, false, false,
                true,
                null,
                false,
                3600L);

        var json = JsonSendMessageResult.from(result);

        assertEquals(JsonSendMessageResult.Type.RATE_LIMIT_FAILURE, json.type());
        assertEquals(3600L, json.retryAfterSeconds());
        assertNull(json.token());
    }

    @Test
    void rateLimitFailureWithoutRetryAfterLeavesFieldNull() {
        var result = new SendMessageResult(ADDRESS,
                false, false, false, false,
                true,
                null,
                false,
                null);

        var json = JsonSendMessageResult.from(result);

        assertEquals(JsonSendMessageResult.Type.RATE_LIMIT_FAILURE, json.type());
        assertNull(json.retryAfterSeconds());
    }

    @Test
    void sendMessageResultsReturnsMaxRetryAfter() {
        var small = new SendMessageResult(ADDRESS,
                false, false, false, false,
                true,
                null,
                false,
                60L);
        var big = new SendMessageResult(new RecipientAddress(null, null, "+15559876543", null),
                false, false, false, false,
                true,
                null,
                false,
                3600L);
        var unknown = new SendMessageResult(new RecipientAddress(null, null, "+15550000000", null),
                false, false, false, false,
                true,
                null,
                false,
                null);

        var aggregate = new SendMessageResults(1L,
                Map.of(new RecipientIdentifier.Uuid(java.util.UUID.randomUUID()), List.of(small, big, unknown)));

        assertEquals(3600L, aggregate.maxRateLimitRetryAfterSeconds());
    }

    @Test
    void sendMessageResultsReturnsNullWhenNoRetryAfter() {
        var noRetry = new SendMessageResult(ADDRESS,
                false, false, false, false,
                true,
                null,
                false,
                null);
        var aggregate = new SendMessageResults(1L,
                Map.of(new RecipientIdentifier.Uuid(java.util.UUID.randomUUID()), List.of(noRetry)));

        assertNull(aggregate.maxRateLimitRetryAfterSeconds());
    }

    @Test
    void successLeavesRetryAfterNull() {
        var result = new SendMessageResult(ADDRESS,
                true, false, false, false,
                false,
                null,
                false,
                null);

        var json = JsonSendMessageResult.from(result);

        assertEquals(JsonSendMessageResult.Type.SUCCESS, json.type());
        assertNull(json.retryAfterSeconds());
    }
}
