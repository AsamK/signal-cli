package org.asamk.signal.manager.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
