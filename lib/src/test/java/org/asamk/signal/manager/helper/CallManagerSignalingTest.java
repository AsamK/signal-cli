package org.asamk.signal.manager.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.CallInfo;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.TestRecipientId;
import org.asamk.signal.manager.util.KeyUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.signal.core.models.ServiceId.ACI;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CallManagerSignalingTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir
    Path directory;
    private SignalAccount account;
    private RecordingCallManager manager;
    private CallManager.CallState state;
    private Map<Long, CallManager.CallState> calls;
    private final StringWriter output = new StringWriter();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        account = SignalAccount.create(directory.toFile(), "account", "+12025550100",
                ACI.parseOrThrow("11111111-1111-4111-8111-111111111111"), ServiceEnvironment.STAGING,
                KeyUtils.generateIdentityKeyPair(), KeyUtils.generateIdentityKeyPair(), KeyUtils.createProfileKey(),
                Settings.DEFAULT);
        manager = new RecordingCallManager(new Context(account, null, null, null, null, null));
        var recipient = account.getRecipientStore().resolveRecipient(
                ACI.parseOrThrow("22222222-2222-4222-8222-222222222222"));
        state = new CallManager.CallState(-1L, CallInfo.State.CONNECTED, recipient, null, true);
        state.controlWriter = new PrintWriter(output, true);
        var field = CallManager.class.getDeclaredField("activeCalls");
        field.setAccessible(true);
        calls = (Map<Long, CallManager.CallState>) field.get(manager);
        calls.put(state.callId, state);
    }

    @AfterEach
    void close() throws Exception {
        if (calls != null) calls.clear();
        if (manager != null) manager.close();
        if (account != null) account.close();
    }

    private void events(String json) throws Exception {
        var method = CallManager.class.getDeclaredMethod("readControlEvents", CallManager.CallState.class, InputStream.class);
        method.setAccessible(true);
        method.invoke(manager, state, new ByteArrayInputStream((json + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void replaysAcceptedNotificationReceivedBeforeReady() throws Exception {
        manager.handleIncomingHangup(state.recipientId, state.callId, 1, HangupMessage.Type.ACCEPTED, 2);
        assertEquals("", output.toString());
        events("{\"type\":\"ready\",\"signalingVersion\":2}");
        var message = MAPPER.readTree(output.toString());
        assertEquals("receivedHangup", message.path("type").asText());
        assertEquals(1, message.path("hangupType").asInt());
        assertEquals(2, message.path("deviceId").asInt());
        assertTrue(state.pendingRemoteNotifications.isEmpty());
    }

    @Test
    void separateCapabilitiesDoNotLoseEarlyNotification() throws Exception {
        manager.handleIncomingHangup(state.recipientId, state.callId, 1, HangupMessage.Type.DECLINED, 2);
        events("{\"type\":\"ready\"}");
        assertEquals("", output.toString());
        events("{\"type\":\"signalingCapabilities\",\"version\":2}");
        assertEquals(2, MAPPER.readTree(output.toString()).path("hangupType").asInt());
    }

    @Test
    void legacyCallEventResolvesPendingNotification() throws Exception {
        manager.handleIncomingHangup(state.recipientId, state.callId, 1, HangupMessage.Type.ACCEPTED, 2);
        events("{\"type\":\"ready\"}\n{\"type\":\"stateChange\",\"state\":\"Connected\"}");
        assertEquals(Boolean.FALSE, state.multiDeviceSignaling);
        assertTrue(state.pendingRemoteNotifications.isEmpty());
        assertEquals("", output.toString());
        assertEquals(CallInfo.State.CONNECTED, state.state);
    }

    @Test
    void doesNotReplayNotificationsAfterCallRemoval() throws Exception {
        manager.handleIncomingHangup(state.recipientId, state.callId, 1, HangupMessage.Type.ACCEPTED, 2);
        calls.clear();
        events("{\"type\":\"ready\",\"signalingVersion\":2}");
        assertEquals("", output.toString());
        assertTrue(state.pendingRemoteNotifications.isEmpty());
    }

    @Test
    void readyNegotiatesBeforeCallEvents() throws Exception {
        events("{\"type\":\"ready\",\"signalingVersion\":2}");
        manager.handleIncomingHangup(state.recipientId, state.callId, 2, HangupMessage.Type.NORMAL, 0);
        var message = MAPPER.readTree(output.toString());
        assertEquals("receivedHangup", message.path("type").asText());
        assertEquals(2, message.path("senderDeviceId").asInt());
        assertEquals(CallInfo.State.CONNECTED, state.state);
        assertTrue(calls.containsKey(state.callId));
    }

    @Test
    void supportsSeparateCapabilityEvent() throws Exception {
        events("{\"type\":\"ready\"}\n{\"type\":\"signalingCapabilities\",\"version\":2}");
        manager.handleIncomingBusy(state.recipientId, state.callId, 7);
        var message = MAPPER.readTree(output.toString());
        assertEquals("receivedBusy", message.path("type").asText());
        assertEquals(7, message.path("senderDeviceId").asInt());
        assertEquals(CallInfo.State.CONNECTED, state.state);
    }

    @Test
    void oldTunnelDelegatesNormalHangupToLegacyCleanup() throws Exception {
        events("{\"type\":\"ready\"}");
        manager.handleIncomingHangup(state.recipientId, state.callId, 1, HangupMessage.Type.NORMAL, 0);
        assertEquals(state.callId, manager.endedCall);
        assertEquals("remote_hangup", manager.endReason);
        assertEquals("", output.toString());
    }

    @Test
    void oldTunnelDelegatesBusyToLegacyCleanup() {
        manager.handleIncomingBusy(state.recipientId, state.callId, 1);
        assertEquals(state.callId, manager.endedCall);
        assertEquals("remote_busy", manager.endReason);
    }

    @Test
    void oldTunnelIgnoresOtherDeviceNotification() {
        manager.handleIncomingHangup(state.recipientId, state.callId, 2, HangupMessage.Type.ACCEPTED, 1);
        assertEquals("", output.toString());
        assertEquals(CallInfo.State.CONNECTED, state.state);
    }

    @Test
    void ignoresWrongSenderAndUnknownCall() throws Exception {
        events("{\"type\":\"ready\",\"signalingVersion\":2}");
        RecipientId stranger = TestRecipientId.createTestId(99999L);
        manager.handleIncomingHangup(stranger, state.callId, 2, HangupMessage.Type.NORMAL, 0);
        manager.handleIncomingBusy(stranger, state.callId, 2);
        manager.handleIncomingHangup(state.recipientId, 42, 2, HangupMessage.Type.NORMAL, 0);
        manager.handleIncomingBusy(state.recipientId, 42, 2);
        assertEquals("", output.toString());
        assertEquals(CallInfo.State.CONNECTED, state.state);
    }

    @Test
    void preservesBroadcastAndExplicitTargetsWithLegacyFallback() throws Exception {
        var method = CallManager.class.getDeclaredMethod("receiverDeviceId", CallManager.CallState.class, JsonNode.class);
        method.setAccessible(true);
        state.deviceId = 3;
        assertEquals(3, method.invoke(null, state, MAPPER.readTree("{}")));
        assertNull(method.invoke(null, state, MAPPER.readTree("{\"receiverDeviceId\":null}")));
        assertEquals(7, method.invoke(null, state, MAPPER.readTree("{\"receiverDeviceId\":7}")));
    }

    @Test
    void acceptedNotificationPreservesAnsweredDeviceAndBroadcast() throws Exception {
        var method = CallManager.class.getDeclaredMethod("hangupMessage", long.class, String.class, int.class, Integer.class);
        method.setAccessible(true);
        var message = (SignalServiceCallMessage) method.invoke(null, -1L, "acceptedonanotherdevice", 1, null);
        assertTrue(message.getDestinationDeviceId().isEmpty());
        var hangup = message.getHangupMessage().orElseThrow();
        assertEquals(HangupMessage.Type.ACCEPTED, hangup.getType());
        assertEquals(1, hangup.getDeviceId());
        assertEquals(-1L, hangup.getId());
    }

    @ParameterizedTest
    @EnumSource(value = CallInfo.State.class, names = {"CONNECTED", "RECONNECTING"})
    void lateAnswerDoesNotReplaceEstablishedState(CallInfo.State established) throws Exception {
        events("{\"type\":\"ready\",\"signalingVersion\":2}");
        state.state = established;
        manager.handleIncomingAnswer(state.callId, 2, new byte[]{1});
        assertEquals(established, state.state);
        assertNull(state.deviceId);
        assertEquals(2, MAPPER.readTree(output.toString()).path("senderDeviceId").asInt());
    }

    private static class RecordingCallManager extends CallManager {
        long endedCall;
        String endReason;

        RecordingCallManager(Context context) {
            super(context);
        }

        @Override
        void endCall(long callId, String reason) {
            endedCall = callId;
            endReason = reason;
        }
    }
}
