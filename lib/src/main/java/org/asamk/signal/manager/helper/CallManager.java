package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.CallInfo;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.TurnServer;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

        // Create per-call socket directory
        var callDir = Files.createTempDirectory(Path.of("/tmp"), "sc-");
        Files.setPosixFilePermissions(callDir, PosixFilePermissions.fromString("rwx------"));
        var controlSocketPath = callDir.resolve("ctrl.sock").toString();

        var state = new CallState(callId,
                CallInfo.State.RINGING_OUTGOING,
                recipientApiAddress,
                recipient,
                true,
                controlSocketPath,
                callDir);
        activeCalls.put(callId, state);

        // Spawn call tunnel binary and connect control channel
        spawnMediaTunnel(state);

        // Fetch TURN servers
        var turnServers = getTurnServers();

        // Send createOutgoingCall + proceed via control channel
        var peerIdStr = recipientAddress.toString();
        sendControlMessage(state, "{\"type\":\"createOutgoingCall\",\"callId\":" + callIdJson(callId)
                + ",\"peerId\":\"" + escapeJson(peerIdStr) + "\"}");
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

        Path callDir;
        try {
            callDir = Files.createTempDirectory(Path.of("/tmp"), "sc-");
            Files.setPosixFilePermissions(callDir, PosixFilePermissions.fromString("rwx------"));
        } catch (IOException e) {
            logger.warn("Failed to create socket directory for incoming call {}", callId, e);
            return;
        }
        var controlSocketPath = callDir.resolve("ctrl.sock").toString();

        var state = new CallState(callId,
                CallInfo.State.RINGING_INCOMING,
                senderAddress,
                senderIdentifier,
                false,
                controlSocketPath,
                callDir);
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
        var opaqueB64 = java.util.Base64.getEncoder().encodeToString(opaque);
        var senderIdKeyB64 = java.util.Base64.getEncoder().encodeToString(remoteIdentityKey);
        var receiverIdKeyB64 = java.util.Base64.getEncoder().encodeToString(localIdentityKey);
        var peerIdStr = senderAddress.toString();
        sendControlMessage(state, "{\"type\":\"receivedOffer\",\"callId\":" + callIdJson(callId)
                + ",\"peerId\":\"" + escapeJson(peerIdStr) + "\""
                + ",\"senderDeviceId\":1"
                + ",\"opaque\":\"" + opaqueB64 + "\""
                + ",\"age\":0"
                + ",\"senderIdentityKey\":\"" + senderIdKeyB64 + "\""
                + ",\"receiverIdentityKey\":\"" + receiverIdKeyB64 + "\""
                + "}");

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
        var opaqueB64 = java.util.Base64.getEncoder().encodeToString(opaque);
        var senderIdKeyB64 = java.util.Base64.getEncoder().encodeToString(remoteIdentityKey);
        var receiverIdKeyB64 = java.util.Base64.getEncoder().encodeToString(localIdentityKey);
        sendControlMessage(state, "{\"type\":\"receivedAnswer\""
                + ",\"opaque\":\"" + opaqueB64 + "\""
                + ",\"senderDeviceId\":1"
                + ",\"senderIdentityKey\":\"" + senderIdKeyB64 + "\""
                + ",\"receiverIdentityKey\":\"" + receiverIdKeyB64 + "\""
                + "}");

        state.state = CallInfo.State.CONNECTING;

        logger.info("Received answer for call {}", callId);
    }

    public void handleIncomingIceCandidate(final long callId, final byte[] opaque) {
        var state = activeCalls.get(callId);
        if (state == null) {
            logger.debug("Received ICE candidate for unknown call {}", callId);
            return;
        }

        // Forward to subprocess as receivedIce
        var b64 = java.util.Base64.getEncoder().encodeToString(opaque);
        sendControlMessage(state, "{\"type\":\"receivedIce\",\"candidates\":[\"" + b64 + "\"]}");
        logger.debug("Forwarded ICE candidate to tunnel for call {}", callId);
    }

    public void handleIncomingHangup(final long callId) {
        endCall(callId, "remote_hangup");
    }

    public void handleIncomingBusy(final long callId) {
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
        var sb = new StringBuilder();
        sb.append("{\"type\":\"proceed\",\"callId\":").append(callIdJson(callId));
        sb.append(",\"hideIp\":false");
        sb.append(",\"iceServers\":[");
        for (int i = 0; i < turnServers.size(); i++) {
            if (i > 0) sb.append(",");
            var ts = turnServers.get(i);
            sb.append("{\"username\":\"").append(escapeJson(ts.username())).append("\"");
            sb.append(",\"password\":\"").append(escapeJson(ts.password())).append("\"");
            sb.append(",\"urls\":[");
            for (int j = 0; j < ts.urls().size(); j++) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(escapeJson(ts.urls().get(j))).append("\"");
            }
            sb.append("]}");
        }
        sb.append("]}");
        sendControlMessage(state, sb.toString());
    }

    private void spawnMediaTunnel(CallState state) {
        try {
            var command = new ArrayList<>(List.of(findTunnelBinary()));
            // Config is sent via stdin; no --host-audio by default

            var processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            var process = processBuilder.start();

            // Write config JSON to stdin
            var config = buildConfig(state);
            try (var stdin = process.getOutputStream()) {
                stdin.write(config.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }

            state.tunnelProcess = process;

            // Drain subprocess stdout/stderr to prevent pipe buffer deadlock
            Thread.ofVirtual().name("tunnel-output-" + state.callId).start(() -> {
                try (var reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.debug("[tunnel-{}] {}", state.callId, line);
                    }
                } catch (IOException ignored) {
                }
            });

            // Connect to control socket in background
            Thread.ofVirtual().name("control-connect-" + state.callId).start(() -> {
                connectToControlSocket(state);
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

        // Check relative to the signal-cli installation
        var installDir = System.getProperty("signal.cli.install.dir");
        if (installDir != null) {
            var binPath = Path.of(installDir, "bin", "signal-call-tunnel");
            if (Files.isExecutable(binPath)) {
                return binPath.toString();
            }
        }

        // Fall back to PATH
        return "signal-call-tunnel";
    }

    private String buildConfig(CallState state) {
        // Generate control channel authentication token
        var tokenBytes = new byte[32];
        new SecureRandom().nextBytes(tokenBytes);
        state.controlToken = java.util.Base64.getEncoder().encodeToString(tokenBytes);

        var sb = new StringBuilder();
        sb.append("{");
        sb.append("\"call_id\":").append(callIdJson(state.callId));
        sb.append(",\"is_outgoing\":").append(state.isOutgoing);
        sb.append(",\"control_socket_path\":\"").append(escapeJson(state.controlSocketPath)).append("\"");
        sb.append(",\"control_token\":\"").append(state.controlToken).append("\"");
        sb.append(",\"local_device_id\":1");
        sb.append("}");
        return sb.toString();
    }

    private void connectToControlSocket(CallState state) {
        var socketPath = Path.of(state.controlSocketPath);
        var addr = UnixDomainSocketAddress.of(socketPath);

        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                Thread.sleep(200);
                if (!Files.exists(socketPath)) continue;

                var channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                channel.connect(addr);
                state.controlChannel = channel;
                state.controlWriter = new PrintWriter(
                        new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8), true);

                // Send authentication token
                state.controlWriter.println("{\"type\":\"auth\",\"token\":\"" + state.controlToken + "\"}");
                logger.info("Connected to control socket for call {}", state.callId);

                // Flush any pending control messages
                for (var msg : state.pendingControlMessages) {
                    state.controlWriter.println(msg);
                }
                state.pendingControlMessages.clear();

                // Start reading control events
                Thread.ofVirtual().name("control-read-" + state.callId).start(() -> {
                    readControlEvents(state);
                });
                return;
            } catch (IOException e) {
                logger.debug("Control socket connect attempt {} failed: {}", attempt, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        logger.warn("Failed to connect to control socket for call {} after retries", state.callId);
    }

    private void readControlEvents(CallState state) {
        try (var reader = new BufferedReader(
                new InputStreamReader(Channels.newInputStream(state.controlChannel), StandardCharsets.UTF_8))) {
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
    }

    private void sendAcceptIfReady(CallState state) {
        if (state.acceptPending && state.controlWriter != null) {
            state.acceptPending = false;
            logger.debug("Sending deferred accept for call {}", state.callId);
            state.controlWriter.println("{\"type\":\"accept\"}");
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

    /** Format call ID as unsigned for JSON (tunnel binary expects u64). */
    private static String callIdJson(long callId) {
        return Long.toUnsignedString(callId);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void endCall(final long callId, final String reason) {
        var state = activeCalls.remove(callId);
        if (state == null) return;

        state.state = CallInfo.State.ENDED;
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

        // Send hangup via control channel before killing process
        if (state.controlWriter != null) {
            try {
                state.controlWriter.println("{\"type\":\"hangup\"}");
            } catch (Exception e) {
                logger.debug("Failed to send hangup via control channel", e);
            }
        }

        // Close control channel
        if (state.controlChannel != null) {
            try {
                state.controlChannel.close();
            } catch (IOException e) {
                logger.debug("Failed to close control channel for call {}", callId, e);
            }
        }

        // Kill tunnel process
        if (state.tunnelProcess != null && state.tunnelProcess.isAlive()) {
            state.tunnelProcess.destroy();
        }

        // Clean up socket directory
        try {
            Files.deleteIfExists(Path.of(state.controlSocketPath));
            Files.deleteIfExists(state.socketDir);
        } catch (IOException e) {
            logger.debug("Failed to clean up socket directory for call {}", callId, e);
        }
    }

    private void handleRingTimeout(final long callId) {
        var state = activeCalls.get(callId);
        if (state == null) return;

        if (state.state == CallInfo.State.RINGING_INCOMING || state.state == CallInfo.State.RINGING_OUTGOING) {
            logger.info("Call {} ring timeout", callId);
            try {
                hangupCall(callId);
            } catch (IOException e) {
                logger.warn("Failed to hangup timed-out call {}", callId, e);
                endCall(callId, "ring_timeout");
            }
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
        final String controlSocketPath;
        final Path socketDir;
        volatile String inputDeviceName;
        volatile String outputDeviceName;
        volatile Process tunnelProcess;
        volatile SocketChannel controlChannel;
        volatile PrintWriter controlWriter;
        volatile String controlToken;
        // Raw offer opaque for incoming calls (forwarded to subprocess)
        volatile byte[] rawOfferOpaque;
        // Control messages queued before the control channel connects
        final List<String> pendingControlMessages = java.util.Collections.synchronizedList(new ArrayList<>());
        // Accept deferred until tunnel reports Ringing state
        volatile boolean acceptPending = false;

        CallState(
                long callId,
                CallInfo.State state,
                org.asamk.signal.manager.api.RecipientAddress recipientAddress,
                RecipientIdentifier.Single recipientIdentifier,
                boolean isOutgoing,
                String controlSocketPath,
                Path socketDir
        ) {
            this.callId = callId;
            this.state = state;
            this.recipientAddress = recipientAddress;
            this.recipientIdentifier = recipientIdentifier;
            this.isOutgoing = isOutgoing;
            this.controlSocketPath = controlSocketPath;
            this.socketDir = socketDir;
        }

        CallInfo toCallInfo() {
            return new CallInfo(callId, state, recipientAddress, inputDeviceName, outputDeviceName, isOutgoing);
        }
    }
}
