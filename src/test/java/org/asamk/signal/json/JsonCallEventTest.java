package org.asamk.signal.json;

import org.asamk.signal.manager.api.CallInfo;
import org.asamk.signal.manager.api.RecipientAddress;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCallEventTest {

    @Test
    void fromWithNumberAndUuid() {
        var recipient = new RecipientAddress("a1b2c3d4-e5f6-7890-abcd-ef1234567890", null, "+15551234567", null);
        var callInfo = new CallInfo(123L, CallInfo.State.CONNECTED, recipient, "signal_input_123", "signal_output_123", true);

        var event = JsonCallEvent.from(callInfo, null);

        assertEquals(123L, event.callId());
        assertEquals("CONNECTED", event.state());
        assertEquals("+15551234567", event.number());
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", event.uuid());
        assertTrue(event.isOutgoing());
        assertEquals("signal_input_123", event.inputDeviceName());
        assertEquals("signal_output_123", event.outputDeviceName());
        assertNull(event.reason());
    }

    @Test
    void fromWithUuidOnly() {
        var recipient = new RecipientAddress("a1b2c3d4-e5f6-7890-abcd-ef1234567890", null, null, null);
        var callInfo = new CallInfo(456L, CallInfo.State.RINGING_INCOMING, recipient, "signal_input_456", "signal_output_456", false);

        var event = JsonCallEvent.from(callInfo, null);

        assertEquals(456L, event.callId());
        assertEquals("RINGING_INCOMING", event.state());
        assertNull(event.number());
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", event.uuid());
        assertFalse(event.isOutgoing());
    }

    @Test
    void fromWithNumberOnly() {
        var recipient = new RecipientAddress(null, null, "+15559876543", null);
        var callInfo = new CallInfo(789L, CallInfo.State.RINGING_OUTGOING, recipient, "signal_input_789", "signal_output_789", true);

        var event = JsonCallEvent.from(callInfo, null);

        assertEquals("+15559876543", event.number());
        assertNull(event.uuid());
    }

    @Test
    void fromWithEndedStateAndReason() {
        var recipient = new RecipientAddress("uuid-1234", null, "+15551111111", null);
        var callInfo = new CallInfo(101L, CallInfo.State.ENDED, recipient, null, null, false);

        var event = JsonCallEvent.from(callInfo, "remote_hangup");

        assertEquals("ENDED", event.state());
        assertEquals("remote_hangup", event.reason());
    }

    @Test
    void fromMapsAllStates() {
        var recipient = new RecipientAddress("uuid-1234", null, "+15551111111", null);

        for (var state : CallInfo.State.values()) {
            var callInfo = new CallInfo(1L, state, recipient, "signal_input_1", "signal_output_1", true);
            var event = JsonCallEvent.from(callInfo, null);
            assertEquals(state.name(), event.state());
        }
    }

    @Test
    void fromConnectingState() {
        var recipient = new RecipientAddress("uuid-5678", null, "+15552222222", null);
        var callInfo = new CallInfo(200L, CallInfo.State.CONNECTING, recipient, "signal_input_200", "signal_output_200", true);

        var event = JsonCallEvent.from(callInfo, null);

        assertEquals(200L, event.callId());
        assertEquals("CONNECTING", event.state());
        assertEquals("signal_input_200", event.inputDeviceName());
        assertEquals("signal_output_200", event.outputDeviceName());
        assertTrue(event.isOutgoing());
        assertNull(event.reason());
    }

    @Test
    void fromWithVariousEndReasons() {
        var recipient = new RecipientAddress("uuid-1234", null, "+15551111111", null);

        var reasons = new String[]{"local_hangup", "remote_hangup", "rejected", "remote_busy",
                "ring_timeout", "ice_failed", "tunnel_exit", "tunnel_error", "shutdown"};

        for (var reason : reasons) {
            var callInfo = new CallInfo(1L, CallInfo.State.ENDED, recipient, null, null, false);
            var event = JsonCallEvent.from(callInfo, reason);
            assertEquals(reason, event.reason());
            assertEquals("ENDED", event.state());
        }
    }
}
