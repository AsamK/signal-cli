package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.CallInfo;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.TurnServer;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages active voice calls: tracks state, spawns/monitors the signal-call-tunnel
 * subprocess, routes incoming call messages, and handles timeouts.
 */
public class CallManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(CallManager.class);
    private static final long RING_TIMEOUT_MS = 60_000;
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Context context;
    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final Map<Long, CallState> activeCalls = new ConcurrentHashMap<>();
    private final List<Manager.CallEventListener> callEventListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "call-timeout-scheduler");
        t.setDaemon(true);
        return t;
    });

    public CallManager(final Context context) {
        this.context = context;
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
    }

    public void addCallEventListener(Manager.CallEventListener listener) {
        callEventListeners.add(listener);
    }

    public void removeCallEventListener(Manager.CallEventListener listener) {
        callEventListeners.remove(listener);
    }

    private void fireCallEvent(CallState state, String reason) {
        var callInfo = state.toCallInfo();
        for (var listener : callEventListeners) {
            try {
                listener.handleCallEvent(callInfo, reason);
            } catch (Throwable e) {
                logger.warn("Call event listener failed, ignoring", e);
            }
        }
    }

    public CallInfo startOutgoingCall(
            final RecipientIdentifier.Single recipient
    ) throws IOException, UnregisteredRecipientException {
        var callId = generateCallId();
        var recipientId = context.getRecipientHelper().resolveRecipient(recipient);
        var recipientAddress = context.getRecipientHelper()
                .resolveSignalServiceAddress(recipientId)
                .getServiceId();
        var recipientApiAddress = account.getRecipientAddressResolver()
                .resolveRecipientAddress(recipientId)
                .toApiRecipientAddress();

        var state = new CallState(callId,
                CallInfo.State.RINGING_OUTGOING,
                recipientApiAddress,
                recipient,
                true);
        activeCalls.put(callId, state);
        fireCallEvent(state, null);

        // Spawn call tunnel binary and connect control channel
        spawnMediaTunnel(state);

        // Fetch TURN servers
        var turnServers = getTurnServers();

        // Send createOutgoingCall + proceed via control channel
        var createMsg = mapper.createObjectNode();
        createMsg.put("type", "createOutgoingCall");
        createMsg.put("callId", callIdUnsigned(callId));
        createMsg.put("peerId", recipientAddress.toString());
        sendControlMessage(state, writeJson(createMsg));
        sendProceed(state, callId, turnServers);

        // Schedule ring timeout
        scheduler.schedule(() -> handleRingTimeout(callId), RING_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        logger.info("Started outgoing call {} to {}", callId, recipient);
        return state.toCallInfo();
    }

    public CallInfo acceptIncomingCall(final long callId) throws IOException {
        var state = activeCalls.get(callId);
        if (state == null) {
            throw new IOException("No active call with id " + callId);
        }
        if (state.state != CallInfo.State.RINGING_INCOMING) {
            throw new IOException("Call " + callId + " is not in RINGING_INCOMING state (current: " + state.state + ")");
        }

        // Defer the accept until the tunnel reports Ringing state.
        // Sending accept too early (while RingRTC is in ConnectingBeforeAccepted)
        // causes it to be silently dropped.
        state.acceptPending = true;
        // If the tunnel is already in Ringing state, send immediately
        sendAcceptIfReady(state);

        state.state = CallInfo.State.CONNECTING;
        fireCallEvent(state, null);

        logger.info("Accepted incoming call {}", callId);
        return state.toCallInfo();
    }

    public void hangupCall(final long callId) throws IOException {
        var state = activeCalls.get(callId);
        if (state == null) {
            throw new IOException("No active call with id " + callId);
        }
        endCall(callId, "local_hangup");
    }

    public void rejectCall(final long callId) throws IOException {
        var state = activeCalls.get(callId);
        if (state == null) {
            throw new IOException("No active call with id " + callId);
        }

        try {
            var recipientId = context.getRecipientHelper().resolveRecipient(state.recipientIdentifier);
            var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
            var busyMessage = new org.whispersystems.signalservice.api.messages.calls.BusyMessage(callId);
            var callMessage = org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage.forBusy(
                    busyMessage, null);
            dependencies.getMessageSender().sendCallMessage(address, null, callMessage);
        } catch (Exception e) {
            logger.warn("Failed to send busy message for call {}", callId, e);
        }

        endCall(callId, "rejected");
    }

    public List<CallInfo> listActiveCalls() {
        return activeCalls.values().stream().map(CallState::toCallInfo).toList();
    }

    public List<TurnServer> getTurnServers() throws IOException {
        try {
            var result = dependencies.getCallingApi().getTurnServerInfo();
            var turnServerList = result.successOrThrow();
            return turnServerList.stream()
                    .map(info -> new TurnServer(info.getUsername(), info.getPassword(), info.getUrls()))
                    .toList();
        } catch (Throwable e) {
            logger.warn("Failed to get TURN server info, returning empty list", e);
            return List.of();
        }
    }

    // --- Incoming call message handling ---

    public void handleIncomingOffer(
            final org.asamk.signal.manager.storage.recipients.RecipientId senderId,
            final long callId,
            final MessageEnvelope.Call.Offer.Type type,
            final byte[] opaque
    ) {
        if (callEventListeners.isEmpty()) {
            logger.debug("Ignoring incoming offer for call {}: no call event listeners registered", callId);
            try {
                var address = context.getRecipientHelper().resolveSignalServiceAddress(senderId);
                var busyMessage = new org.whispersystems.signalservice.api.messages.calls.BusyMessage(callId);
                var callMessage = org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage.forBusy(
                        busyMessage, null);
                dependencies.getMessageSender().sendCallMessage(address, null, callMessage);
            } catch (Exception e) {
                logger.warn("Failed to send busy for unhandled call {}", callId, e);
            }
            return;
        }

        var senderAddress = account.getRecipientAddressResolver()
                .resolveRecipientAddress(senderId)
                .toApiRecipientAddress();

        RecipientIdentifier.Single senderIdentifier;
        if (senderAddress.number().isPresent()) {
            senderIdentifier = new RecipientIdentifier.Number(senderAddress.number().get());
        } else if (senderAddress.uuid().isPresent()) {
            senderIdentifier = new RecipientIdentifier.Uuid(senderAddress.uuid().get());
        } else {
            logger.warn("Cannot identify sender for call {}", callId);
            return;
        }

        logger.debug("Incoming offer opaque ({} bytes)", opaque == null ? 0 : opaque.length);

        var state = new CallState(callId,
                CallInfo.State.RINGING_INCOMING,
                senderAddress,
                senderIdentifier,
                false);
        state.rawOfferOpaque = opaque;
        activeCalls.put(callId, state);

        // Spawn call tunnel binary immediately
        spawnMediaTunnel(state);

        // Get identity keys for the receivedOffer message
        // Use raw 32-byte Curve25519 public key (without 0x05 DJB prefix) to match Signal Android
        byte[] localIdentityKey = getRawIdentityKeyBytes(account.getAciIdentityKeyPair().getPublicKey().serialize());
        byte[] remoteIdentityKey = getRemoteIdentityKey(state);

        // Fetch TURN servers
        List<TurnServer> turnServers;
        try {
            turnServers = getTurnServers();
        } catch (IOException e) {
            logger.warn("Failed to get TURN servers for incoming call {}", callId, e);
            turnServers = List.of();
        }

        // Send receivedOffer to subprocess
        var offerMsg = mapper.createObjectNode();
        offerMsg.put("type", "receivedOffer");
        offerMsg.put("callId", callIdUnsigned(callId));
        offerMsg.put("peerId", senderAddress.toString());
        offerMsg.put("senderDeviceId", 1);
        offerMsg.put("opaque", java.util.Base64.getEncoder().encodeToString(opaque));
        offerMsg.put("age", 0);
        offerMsg.put("senderIdentityKey", java.util.Base64.getEncoder().encodeToString(remoteIdentityKey));
        offerMsg.put("receiverIdentityKey", java.util.Base64.getEncoder().encodeToString(localIdentityKey));
        sendControlMessage(state, writeJson(offerMsg));

        // Send proceed with TURN servers
        sendProceed(state, callId, turnServers);

        fireCallEvent(state, null);

        // Schedule ring timeout
        scheduler.schedule(() -> handleRingTimeout(callId), RING_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        logger.info("Incoming call {} from {}", callId, senderAddress);
    }

    public void handleIncomingAnswer(final long callId, final byte[] opaque) {
        var state = activeCalls.get(callId);
        if (state == null) {
            logger.warn("Received answer for unknown call {}", callId);
            return;
        }

        // Get identity keys
        // Use raw 32-byte Curve25519 public key (without 0x05 DJB prefix) to match Signal Android
        byte[] localIdentityKey = getRawIdentityKeyBytes(account.getAciIdentityKeyPair().getPublicKey().serialize());
        byte[] remoteIdentityKey = getRemoteIdentityKey(state);

        // Forward raw opaque to subprocess
        var answerMsg = mapper.createObjectNode();
        answerMsg.put("type", "receivedAnswer");
        answerMsg.put("opaque", java.util.Base64.getEncoder().encodeToString(opaque));
        answerMsg.put("senderDeviceId", 1);
        answerMsg.put("senderIdentityKey", java.util.Base64.getEncoder().encodeToString(remoteIdentityKey));
        answerMsg.put("receiverIdentityKey", java.util.Base64.getEncoder().encodeToString(localIdentityKey));
        sendControlMessage(state, writeJson(answerMsg));

        state.state = CallInfo.State.CONNECTING;
        fireCallEvent(state, null);

        logger.info("Received answer for call {}", callId);
    }

    public void handleIncomingIceCandidate(final long callId, final byte[] opaque) {
        var state = activeCalls.get(callId);
        if (state == null) {
            logger.debug("Received ICE candidate for unknown call {}", callId);
            return;
        }

        // Forward to subprocess as receivedIce
        var iceMsg = mapper.createObjectNode();
        iceMsg.put("type", "receivedIce");
        var candidates = iceMsg.putArray("candidates");
        candidates.add(java.util.Base64.getEncoder().encodeToString(opaque));
        sendControlMessage(state, writeJson(iceMsg));
        logger.debug("Forwarded ICE candidate to tunnel for call {}", callId);
    }

    public void handleIncomingHangup(final long callId) {
        if (callEventListeners.isEmpty() && !activeCalls.containsKey(callId)) {
            return;
        }
        endCall(callId, "remote_hangup");
    }

    public void handleIncomingBusy(final long callId) {
        if (callEventListeners.isEmpty() && !activeCalls.containsKey(callId)) {
            return;
        }
        endCall(callId, "remote_busy");
    }

    // --- Internal helpers ---

    private void sendControlMessage(CallState state, String json) {
        if (state.controlWriter == null) {
            logger.debug("Queueing control message for call {} (not yet connected): {}", state.callId, json);
            state.pendingControlMessages.add(json);
            return;
        }
        state.controlWriter.println(json);
    }

    private void sendProceed(CallState state, long callId, List<TurnServer> turnServers) {
        var proceedMsg = mapper.createObjectNode();
        proceedMsg.put("type", "proceed");
        proceedMsg.put("callId", callIdUnsigned(callId));
        proceedMsg.put("hideIp", false);
        var iceServers = proceedMsg.putArray("iceServers");
        for (var ts : turnServers) {
            var server = iceServers.addObject();
            server.put("username", ts.username());
            server.put("password", ts.password());
            var urls = server.putArray("urls");
            for (var url : ts.urls()) {
                urls.add(url);
            }
        }
        sendControlMessage(state, writeJson(proceedMsg));
    }

    private void spawnMediaTunnel(CallState state) {
        try {
            var command = new ArrayList<>(List.of(findTunnelBinary()));

            var processBuilder = new ProcessBuilder(command);
            // Keep stdout and stderr separate: stdout = control protocol, stderr = logging
            processBuilder.redirectErrorStream(false);
            var process = processBuilder.start();

            state.tunnelProcess = process;

            // Write config JSON to stdin, then keep stdin open for control messages
            var config = buildConfig(state);
            var stdinStream = process.getOutputStream();
            stdinStream.write(config.getBytes(StandardCharsets.UTF_8));
            stdinStream.write('\n');
            stdinStream.flush();

            // stdin is the control write channel
            state.controlWriter = new PrintWriter(
                    new OutputStreamWriter(stdinStream, StandardCharsets.UTF_8), true);

            // Flush any pending control messages
            for (var msg : state.pendingControlMessages) {
                state.controlWriter.println(msg);
            }
            state.pendingControlMessages.clear();

            // If accept was deferred, send it now
            sendAcceptIfReady(state);

            // Read control events from subprocess stdout
            Thread.ofVirtual().name("control-read-" + state.callId).start(() -> {
                readControlEvents(state, process.getInputStream());
            });

            // Drain subprocess stderr to prevent pipe buffer deadlock
            Thread.ofVirtual().name("tunnel-stderr-" + state.callId).start(() -> {
                try (var reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.debug("[tunnel-{}] {}", state.callId, line);
                    }
                } catch (IOException ignored) {
                }
            });

            // Monitor process exit
            process.onExit().thenAcceptAsync(p -> {
                logger.info("Tunnel for call {} exited with code {}", state.callId, p.exitValue());
                if (activeCalls.containsKey(state.callId)) {
                    endCall(state.callId, "tunnel_exit");
                }
            });

            logger.info("Spawned signal-call-tunnel for call {}", state.callId);
        } catch (Exception e) {
            logger.error("Failed to spawn tunnel for call {}", state.callId, e);
            endCall(state.callId, "tunnel_spawn_error");
        }
    }

    private String findTunnelBinary() {
        // Check environment variable first
        var envPath = System.getenv("SIGNAL_CALL_TUNNEL_BIN");
        if (envPath != null && !envPath.isEmpty()) {
            return envPath;
        }

        // Check relative to the signal-cli installation directory
        try {
            var codeSource = CallManager.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                var jarPath = Path.of(codeSource.getLocation().toURI());
                var binPath = tunnelBinaryFromCodeSourcePath(jarPath);
                if (Files.isExecutable(binPath)) {
                    return binPath.toString();
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to determine install dir from code source", e);
        }

        // Fall back to PATH
        return "signal-call-tunnel";
    }

    /**
     * Resolves the expected tunnel binary path from a code source path.
     * The code source (jar or class dir) is expected to be in {@code <install>/lib/},
     * so we go up two levels to reach the install root, then look for
     * {@code bin/signal-call-tunnel}.
     */
    static Path tunnelBinaryFromCodeSourcePath(Path codeSourcePath) {
        var installDir = codeSourcePath.getParent().getParent();
        return installDir.resolve("bin").resolve("signal-call-tunnel");
    }

    private String buildConfig(CallState state) {
        var config = mapper.createObjectNode();
        config.put("call_id", callIdUnsigned(state.callId));
        config.put("is_outgoing", state.isOutgoing);
        config.put("local_device_id", 1);
        return writeJson(config);
    }

    private void readControlEvents(CallState state, java.io.InputStream inputStream) {
        try (var reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                logger.debug("Control event for call {}: {}", state.callId, line);

                try {
                    var json = mapper.readTree(line);
                    var type = json.has("type") ? json.get("type").asText() : "";

                    switch (type) {
                        case "ready" -> {
                            if (json.has("inputDeviceName")) {
                                state.inputDeviceName = json.get("inputDeviceName").asText();
                            }
                            if (json.has("outputDeviceName")) {
                                state.outputDeviceName = json.get("outputDeviceName").asText();
                            }
                            logger.debug("Tunnel ready for call {}: input={}, output={}",
                                    state.callId, state.inputDeviceName, state.outputDeviceName);
                        }
                        case "sendOffer" -> {
                            var opaqueB64 = json.get("opaque").asText();
                            var opaque = java.util.Base64.getDecoder().decode(opaqueB64);
                            sendOfferViaSignal(state, opaque);
                        }
                        case "sendAnswer" -> {
                            var opaqueB64 = json.get("opaque").asText();
                            var opaque = java.util.Base64.getDecoder().decode(opaqueB64);
                            sendAnswerViaSignal(state, opaque);
                        }
                        case "sendIce" -> {
                            var candidatesArr = json.get("candidates");
                            var opaqueList = new ArrayList<byte[]>();
                            for (var c : candidatesArr) {
                                opaqueList.add(java.util.Base64.getDecoder().decode(c.get("opaque").asText()));
                            }
                            sendIceViaSignal(state, opaqueList);
                        }
                        case "sendHangup" -> {
                            // RingRTC wants us to send a hangup message via Signal protocol.
                            // This is NOT a local state change — local state is handled by stateChange events.
                            var hangupType = json.has("hangupType") ? json.get("hangupType").asText("normal") : "normal";
                            // Skip multi-device hangup types — signal-cli is single-device,
                            // and sending these to the remote peer causes it to terminate the call.
                            if (hangupType.contains("onanotherdevice")) {
                                logger.debug("Ignoring multi-device hangup type: {}", hangupType);
                            } else {
                                sendHangupViaSignal(state, hangupType);
                            }
                        }
                        case "sendBusy" -> {
                            sendBusyViaSignal(state);
                        }
                        case "stateChange" -> {
                            var ringrtcState = json.get("state").asText();
                            var reason = json.has("reason") ? json.get("reason").asText(null) : null;
                            handleStateChange(state, ringrtcState, reason);
                        }
                        case "error" -> {
                            var message = json.has("message") ? json.get("message").asText("unknown") : "unknown";
                            logger.error("Tunnel error for call {}: {}", state.callId, message);
                            endCall(state.callId, "tunnel_error");
                        }
                        default -> {
                            logger.debug("Unknown control event type '{}' for call {}", type, state.callId);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse control event JSON for call {}: {}", state.callId, e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.debug("Control read ended for call {}: {}", state.callId, e.getMessage());
        }
    }

    private void handleStateChange(CallState state, String ringrtcState, String reason) {
        if (ringrtcState.startsWith("Incoming")) {
            // Don't downgrade if we've already accepted
            if (state.state == CallInfo.State.CONNECTING) return;
            state.state = CallInfo.State.RINGING_INCOMING;
        } else if (ringrtcState.startsWith("Outgoing")) {
            state.state = CallInfo.State.RINGING_OUTGOING;
        } else if ("Ringing".equals(ringrtcState)) {
            // Tunnel is now ready to accept — flush deferred accept if pending
            state.tunnelRinging = true;
            sendAcceptIfReady(state);
            return;
        } else if ("Connected".equals(ringrtcState)) {
            state.state = CallInfo.State.CONNECTED;
        } else if ("Connecting".equals(ringrtcState)) {
            state.state = CallInfo.State.RECONNECTING;
        } else if ("Ended".equals(ringrtcState) || "Rejected".equals(ringrtcState)) {
            endCall(state.callId, reason != null ? reason : ringrtcState.toLowerCase());
            return;
        } else if ("Concluded".equals(ringrtcState)) {
            // Cleanup, no-op
            return;
        }
        fireCallEvent(state, reason);
    }

    private void sendAcceptIfReady(CallState state) {
        if (state.acceptPending && state.tunnelRinging && state.controlWriter != null) {
            state.acceptPending = false;
            logger.debug("Sending deferred accept for call {}", state.callId);
            var acceptMsg = mapper.createObjectNode();
            acceptMsg.put("type", "accept");
            state.controlWriter.println(writeJson(acceptMsg));
        }
    }

    private void sendOfferViaSignal(CallState state, byte[] opaque) {
        try {
            var recipientId = context.getRecipientHelper().resolveRecipient(state.recipientIdentifier);
            var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
            var offerMessage = new org.whispersystems.signalservice.api.messages.calls.OfferMessage(state.callId,
                    org.whispersystems.signalservice.api.messages.calls.OfferMessage.Type.AUDIO_CALL,
                    opaque);
            var callMessage = org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage.forOffer(
                    offerMessage, null);
            dependencies.getMessageSender().sendCallMessage(address, null, callMessage);
            logger.info("Sent offer via Signal for call {}", state.callId);
        } catch (Exception e) {
            logger.warn("Failed to send offer for call {}", state.callId, e);
        }
    }

    private void sendAnswerViaSignal(CallState state, byte[] opaque) {
        try {
            var recipientId = context.getRecipientHelper().resolveRecipient(state.recipientIdentifier);
            var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
            var answerMessage = new org.whispersystems.signalservice.api.messages.calls.AnswerMessage(state.callId, opaque);
            var callMessage = org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage.forAnswer(
                    answerMessage, null);
            dependencies.getMessageSender().sendCallMessage(address, null, callMessage);
            logger.info("Sent answer via Signal for call {}", state.callId);
        } catch (Exception e) {
            logger.warn("Failed to send answer for call {}", state.callId, e);
        }
    }

    private void sendIceViaSignal(CallState state, List<byte[]> opaqueList) {
        try {
            var recipientId = context.getRecipientHelper().resolveRecipient(state.recipientIdentifier);
            var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
            var iceUpdates = opaqueList.stream()
                    .map(opaque -> new org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage(
                            state.callId, opaque))
                    .toList();
            var callMessage = org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage.forIceUpdates(
                    iceUpdates, null);
            dependencies.getMessageSender().sendCallMessage(address, null, callMessage);
            logger.info("Sent {} ICE candidates via Signal for call {}", opaqueList.size(), state.callId);
        } catch (Exception e) {
            logger.warn("Failed to send ICE for call {}", state.callId, e);
        }
    }

    private void sendBusyViaSignal(CallState state) {
        try {
            var recipientId = context.getRecipientHelper().resolveRecipient(state.recipientIdentifier);
            var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
            var busyMessage = new org.whispersystems.signalservice.api.messages.calls.BusyMessage(state.callId);
            var callMessage = org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage.forBusy(
                    busyMessage, null);
            dependencies.getMessageSender().sendCallMessage(address, null, callMessage);
        } catch (Exception e) {
            logger.warn("Failed to send busy for call {}", state.callId, e);
        }
    }

    private void sendHangupViaSignal(CallState state, String hangupType) {
        try {
            var recipientId = context.getRecipientHelper().resolveRecipient(state.recipientIdentifier);
            var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
            var type = switch (hangupType) {
                case "accepted", "acceptedonanotherdevice" ->
                        org.whispersystems.signalservice.api.messages.calls.HangupMessage.Type.ACCEPTED;
                case "declined", "declinedonanotherdevice" ->
                        org.whispersystems.signalservice.api.messages.calls.HangupMessage.Type.DECLINED;
                case "busy", "busyonanotherdevice" ->
                        org.whispersystems.signalservice.api.messages.calls.HangupMessage.Type.BUSY;
                default -> org.whispersystems.signalservice.api.messages.calls.HangupMessage.Type.NORMAL;
            };
            var hangupMessage = new org.whispersystems.signalservice.api.messages.calls.HangupMessage(
                    state.callId, type, 0);
            var callMessage = org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage.forHangup(
                    hangupMessage, null);
            dependencies.getMessageSender().sendCallMessage(address, null, callMessage);
            logger.info("Sent hangup ({}) via Signal for call {}", hangupType, state.callId);
        } catch (Exception e) {
            logger.warn("Failed to send hangup for call {}", state.callId, e);
        }
    }

    private byte[] getRemoteIdentityKey(CallState state) {
        try {
            var recipientId = context.getRecipientHelper().resolveRecipient(state.recipientIdentifier);
            var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
            var serviceId = address.getServiceId();
            var identityInfo = account.getIdentityKeyStore().getIdentityInfo(serviceId);
            if (identityInfo != null) {
                return getRawIdentityKeyBytes(identityInfo.getIdentityKey().serialize());
            }
        } catch (Exception e) {
            logger.warn("Failed to get remote identity key for call {}", state.callId, e);
        }
        logger.warn("Using local identity key as fallback for remote identity key");
        return getRawIdentityKeyBytes(account.getAciIdentityKeyPair().getPublicKey().serialize());
    }

    /**
     * Strip the 0x05 DJB type prefix from a serialized identity key to get the
     * raw 32-byte Curve25519 public key. Signal Android does this via
     * WebRtcUtil.getPublicKeyBytes() before passing keys to RingRTC.
     */
    private static byte[] getRawIdentityKeyBytes(byte[] serializedKey) {
        if (serializedKey.length == 33 && serializedKey[0] == 0x05) {
            return java.util.Arrays.copyOfRange(serializedKey, 1, serializedKey.length);
        }
        return serializedKey;
    }

    /** Convert signed long call ID to unsigned BigInteger (tunnel binary expects u64). */
    private static BigInteger callIdUnsigned(long callId) {
        return new BigInteger(Long.toUnsignedString(callId));
    }

    private static String writeJson(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    private void endCall(final long callId, final String reason) {
        var state = activeCalls.remove(callId);
        if (state == null) return;

        state.state = CallInfo.State.ENDED;
        fireCallEvent(state, reason);
        logger.info("Call {} ended: {}", callId, reason);

        // Send Signal protocol hangup to remote peer (unless they initiated the end)
        if (!"remote_hangup".equals(reason) && !"rejected".equals(reason) && !"remote_busy".equals(reason)
                && !"ringrtc_hangup".equals(reason)) {
            try {
                var recipientId = context.getRecipientHelper().resolveRecipient(state.recipientIdentifier);
                var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
                var hangupMessage = new org.whispersystems.signalservice.api.messages.calls.HangupMessage(callId,
                        org.whispersystems.signalservice.api.messages.calls.HangupMessage.Type.NORMAL, 0);
                var callMessage = org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage.forHangup(
                        hangupMessage, null);
                dependencies.getMessageSender().sendCallMessage(address, null, callMessage);
            } catch (Exception e) {
                logger.warn("Failed to send hangup to remote for call {}", callId, e);
            }
        }

        // Send hangup via control channel (stdin) before killing process
        if (state.controlWriter != null) {
            try {
                var hangupMsg = mapper.createObjectNode();
                hangupMsg.put("type", "hangup");
                state.controlWriter.println(writeJson(hangupMsg));
                state.controlWriter.close();
            } catch (Exception e) {
                logger.debug("Failed to send hangup via control channel", e);
            }
        }

        // Kill tunnel process
        if (state.tunnelProcess != null && state.tunnelProcess.isAlive()) {
            state.tunnelProcess.destroy();
        }
    }

    private void handleRingTimeout(final long callId) {
        var state = activeCalls.get(callId);
        if (state == null) return;

        if (state.state == CallInfo.State.RINGING_INCOMING || state.state == CallInfo.State.RINGING_OUTGOING) {
            logger.info("Call {} ring timeout", callId);
            endCall(callId, "ring_timeout");
        }
    }

    private static long generateCallId() {
        return new SecureRandom().nextLong() & Long.MAX_VALUE;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        for (var callId : new ArrayList<>(activeCalls.keySet())) {
            endCall(callId, "shutdown");
        }
    }

    // --- Internal call state tracking ---

    static class CallState {

        final long callId;
        volatile CallInfo.State state;
        final org.asamk.signal.manager.api.RecipientAddress recipientAddress;
        final RecipientIdentifier.Single recipientIdentifier;
        final boolean isOutgoing;
        volatile String inputDeviceName;
        volatile String outputDeviceName;
        volatile Process tunnelProcess;
        volatile PrintWriter controlWriter;
        // Raw offer opaque for incoming calls (forwarded to subprocess)
        volatile byte[] rawOfferOpaque;
        // Control messages queued before the tunnel process starts
        final List<String> pendingControlMessages = java.util.Collections.synchronizedList(new ArrayList<>());
        // Accept deferred until tunnel reports Ringing state
        volatile boolean acceptPending = false;
        // True once the tunnel has reported "Ringing" (ready to accept)
        volatile boolean tunnelRinging = false;

        CallState(
                long callId,
                CallInfo.State state,
                org.asamk.signal.manager.api.RecipientAddress recipientAddress,
                RecipientIdentifier.Single recipientIdentifier,
                boolean isOutgoing
        ) {
            this.callId = callId;
            this.state = state;
            this.recipientAddress = recipientAddress;
            this.recipientIdentifier = recipientIdentifier;
            this.isOutgoing = isOutgoing;
        }

        CallInfo toCallInfo() {
            return new CallInfo(callId, state, recipientAddress, inputDeviceName, outputDeviceName, isOutgoing);
        }
    }
}
