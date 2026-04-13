package org.asamk.signal.json;

import org.asamk.signal.manager.api.RecipientAddress;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendMessageResult;
import org.asamk.signal.manager.api.SendMessageResults;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Test
    void aggregateReturnsLongestRetryAfter() {
        var small = rateLimited("+15551234567", 60L);
        var big = rateLimited("+15559876543", 3600L);
        var unknown = rateLimited("+15550000000", null);

        var aggregate = new SendMessageResults(1L,
                Map.of(new RecipientIdentifier.Uuid(UUID.randomUUID()), List.of(small, big, unknown)));

        assertEquals(3600L, aggregate.maxRateLimitRetryAfterSeconds());
    }

    @Test
    void aggregateReturnsNullWhenNoRetryAfter() {
        var aggregate = new SendMessageResults(1L,
                Map.of(new RecipientIdentifier.Uuid(UUID.randomUUID()),
                        List.of(rateLimited("+15551234567", null))));

        assertNull(aggregate.maxRateLimitRetryAfterSeconds());
    }

    /**
     * Regression for a bug where the aggregate helper could overlook the longest
     * wait if only some recipients reported a value. Ensures the max is picked
     * across any mix — which is what downstream captcha/rate-limit clients rely on.
     */
    @Test
    void aggregatePicksMaxEvenWhenSomeValuesAreNull() {
        var withValue = rateLimited("+15551111111", 7200L);
        var withoutValue = rateLimited("+15552222222", null);
        var alsoWithValue = rateLimited("+15553333333", 120L);

        var aggregate = new SendMessageResults(1L,
                Map.of(new RecipientIdentifier.Uuid(UUID.randomUUID()),
                        List.of(withoutValue, withValue, alsoWithValue)));

        assertEquals(7200L, aggregate.maxRateLimitRetryAfterSeconds());
    }

    private static SendMessageResult rateLimited(String number, Long retryAfterSeconds) {
        return new SendMessageResult(new RecipientAddress(null, null, number, null),
                false, false, false, false,
                true,
                null,
                false,
                retryAfterSeconds);
    }
}
