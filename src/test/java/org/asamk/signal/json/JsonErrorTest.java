package org.asamk.signal.json;

import org.asamk.signal.manager.api.InvalidEnvelopeContentException;
import org.asamk.signal.util.Util;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonErrorTest {

    @Test
    void invalidEnvelopeContentIncludesStructuredDetails() throws Exception {
        final var invalidRange = new InvalidEnvelopeContentException.InvalidBodyRange(2, 8, 4, "STYLE_BOLD");
        final var exception = new InvalidEnvelopeContentException("invalid body range",
                InvalidEnvelopeContentException.DATA_MESSAGE_BODY_RANGE_OUT_OF_BOUNDS,
                null,
                3,
                10,
                List.of(invalidRange),
                new Throwable());

        final var error = JsonError.from(exception);

        assertEquals("invalid body range", error.message());
        assertEquals("InvalidEnvelopeContentException", error.type());
        assertEquals(InvalidEnvelopeContentException.DATA_MESSAGE_BODY_RANGE_OUT_OF_BOUNDS,
                error.details().code());
        assertEquals(10, error.details().bodyLength());
        assertEquals(List.of(invalidRange), error.details().invalidBodyRanges());
        final var json = Util.createJsonObjectMapper().writeValueAsString(error);
        assertTrue(json.contains("\"code\":\"DATA_MESSAGE_BODY_RANGE_OUT_OF_BOUNDS\""));
        assertTrue(json.contains("\"start\":8"));
    }

    @Test
    void ordinaryExceptionDoesNotIncludeDetails() throws Exception {
        final var error = JsonError.from(new IllegalArgumentException("bad argument"));

        assertEquals("bad argument", error.message());
        assertEquals("IllegalArgumentException", error.type());
        assertNull(error.details());
        final var json = Util.createJsonObjectMapper().writeValueAsString(error);
        assertFalse(json.contains("details"));
    }
}
