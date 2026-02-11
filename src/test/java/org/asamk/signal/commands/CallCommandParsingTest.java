package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that call commands correctly handle call IDs from JSON-RPC,
 * where Jackson may deserialize large numbers as BigInteger instead of Long.
 */
class CallCommandParsingTest {

    /**
     * Simulates what Jackson produces for a JSON-RPC call with a large call ID.
     * Jackson deserializes numbers that overflow int as BigInteger in untyped maps.
     */
    private static Namespace namespaceWithBigIntegerCallId(long value) {
        // JsonRpcNamespace converts "call-id" to "callId" lookup
        return new JsonRpcNamespace(Map.of("callId", BigInteger.valueOf(value)));
    }

    private static Namespace namespaceWithLongCallId(long value) {
        return new JsonRpcNamespace(Map.of("callId", value));
    }

    @Test
    void hangupCallHandlesBigIntegerCallId() {
        var ns = namespaceWithBigIntegerCallId(8230211930154373276L);
        var callIdNumber = ns.get("call-id");
        long callId = ((Number) callIdNumber).longValue();
        assertEquals(8230211930154373276L, callId);
    }

    @Test
    void hangupCallHandlesLongCallId() {
        var ns = namespaceWithLongCallId(8230211930154373276L);
        var callIdNumber = ns.get("call-id");
        long callId = ((Number) callIdNumber).longValue();
        assertEquals(8230211930154373276L, callId);
    }

    @Test
    void acceptCallHandlesBigIntegerCallId() {
        var ns = namespaceWithBigIntegerCallId(1234567890123456789L);
        var callIdNumber = ns.get("call-id");
        long callId = ((Number) callIdNumber).longValue();
        assertEquals(1234567890123456789L, callId);
    }

    @Test
    void rejectCallHandlesBigIntegerCallId() {
        var ns = namespaceWithBigIntegerCallId(Long.MAX_VALUE);
        var callIdNumber = ns.get("call-id");
        long callId = ((Number) callIdNumber).longValue();
        assertEquals(Long.MAX_VALUE, callId);
    }

    @Test
    void camelCaseKeyLookupWorks() {
        // Verify JsonRpcNamespace maps "call-id" -> "callId"
        var ns = new JsonRpcNamespace(Map.of("callId", BigInteger.valueOf(42L)));
        Number result = ns.get("call-id");
        assertEquals(42L, result.longValue());
    }

    @Test
    void smallIntegerCallIdWorks() {
        // Jackson may produce Integer for small values
        var ns = new JsonRpcNamespace(Map.of("callId", 42));
        var callIdNumber = ns.get("call-id");
        long callId = ((Number) callIdNumber).longValue();
        assertEquals(42L, callId);
    }
}
