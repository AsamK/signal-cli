package org.asamk.signal.manager.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SendMessageResultTest {

    /**
     * Ceiling division — we must never advise a retry before the server's deadline,
     * so sub-second values round up rather than truncate toward zero.
     */
    @Test
    void millisToCeilingSecondsRoundsUp() {
        assertEquals(0L, SendMessageResult.millisToCeilingSeconds(0L));
        assertEquals(1L, SendMessageResult.millisToCeilingSeconds(1L));
        assertEquals(1L, SendMessageResult.millisToCeilingSeconds(500L));
        assertEquals(1L, SendMessageResult.millisToCeilingSeconds(999L));
        assertEquals(1L, SendMessageResult.millisToCeilingSeconds(1000L));
        assertEquals(2L, SendMessageResult.millisToCeilingSeconds(1001L));
        assertEquals(2L, SendMessageResult.millisToCeilingSeconds(1500L));
        assertEquals(60L, SendMessageResult.millisToCeilingSeconds(60_000L));
    }

    /**
     * Source-compat: callers built against the pre-retry-after record shape use the 8-arg
     * constructor. It must continue to compile and produce a record with a null retry-after.
     */
    @Test
    @SuppressWarnings("deprecation")
    void legacyEightArgConstructorPreservesSourceCompat() {
        var result = new SendMessageResult(new RecipientAddress(null, null, "+15551234567", null),
                true, false, false, false, false, null, false);

        assertNull(result.rateLimitRetryAfterSeconds());
    }
}
