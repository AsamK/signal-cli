# Voice Call Support

## Overview

signal-cli supports voice calls by spawning a subprocess called
`signal-call-tunnel` for each call. The tunnel handles WebRTC negotiation and
audio transport. signal-cli communicates with the tunnel over its stdin/stdout
using newline-delimited JSON messages, relaying signaling between the tunnel
and the Signal protocol.

```
signal-cli                        signal-call-tunnel
    |                                     |
    |-- spawn --------------------------->|
    |-- config JSON on stdin ------------>|
    |                                     |
    |-- commands on stdin --------------->|
    |<-- events on stdout ----------------|
    |                                     |   WebRTC
    |   signaling relay                   |   audio I/O
    |                                     |
    |   (stderr: tunnel logging) -------->| (captured by signal-cli)
```

Each call gets its own tunnel process. When the call ends, signal-cli closes
stdin and destroys the process.

Audio device names (`inputDeviceName`, `outputDeviceName`) are opaque strings
returned by the tunnel in its `ready` message. signal-cli passes them through
to JSON-RPC clients, which use them to connect audio via platform APIs.

---

## Spawning the Tunnel

For each call, signal-cli:

1. Spawns `signal-call-tunnel`
2. Writes config JSON followed by a newline to stdin
3. Keeps stdin open for subsequent control messages
4. Reads control events from stdout
5. Captures stderr for logging

The `signal-call-tunnel` binary is located by searching (in order):

1. `SIGNAL_CALL_TUNNEL_BIN` environment variable
2. `<signal-cli install dir>/bin/signal-call-tunnel` (detected from jar location)
3. `signal-call-tunnel` on `PATH`

### Config JSON

The first line written to the tunnel's stdin:

```json
{
  "call_id": 12345,
  "is_outgoing": true,
  "local_device_id": 1,
  "input_device_name": "signal_input",
  "output_device_name": "signal_output"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `call_id` | unsigned 64-bit integer | Call identifier (use unsigned representation) |
| `is_outgoing` | boolean | Whether this is an outgoing call |
| `local_device_id` | integer | Signal device ID |
| `input_device_name` | string (optional) | Requested input audio device name |
| `output_device_name` | string (optional) | Requested output audio device name |

If `input_device_name` or `output_device_name` are omitted, the tunnel
chooses default names. On Linux, these are per-call unique names (e.g.,
`signal_input_<call_id>`). On macOS, these are the fixed names `signal_input`
and `signal_output`, which must match the pre-installed BlackHole drivers.

---

## Control Protocol

Newline-delimited JSON messages over stdin (signal-cli to tunnel) and stdout
(tunnel to signal-cli). The first line on stdin is the config JSON. Subsequent
lines are control messages.

### signal-cli -> Tunnel (stdin)

| Type | When | Fields |
|------|------|--------|
| `createOutgoingCall` | Outgoing call setup | `callId`, `peerId` |
| `proceed` | After offer/receivedOffer | `callId`, `hideIp`, `iceServers` |
| `receivedOffer` | Incoming call | `callId`, `peerId`, `opaque`, `age`, `senderDeviceId`, `senderIdentityKey`, `receiverIdentityKey` |
| `receivedAnswer` | Outgoing call answered | `opaque`, `senderDeviceId`, `senderIdentityKey`, `receiverIdentityKey` |
| `receivedIce` | ICE candidates arrive | `candidates` (array of base64 opaque blobs) |
| `accept` | User accepts incoming call | *(none)* |
| `hangup` | End the call | *(none)* |

### Tunnel -> signal-cli (stdout)

| Type | When | Fields |
|------|------|--------|
| `ready` | Control socket bound, audio devices created | `inputDeviceName`, `outputDeviceName` |
| `sendOffer` | Tunnel generated an offer | `callId`, `opaque`, `callMediaType` |
| `sendAnswer` | Tunnel generated an answer | `callId`, `opaque` |
| `sendIce` | ICE candidates gathered | `callId`, `candidates` (array of `{"opaque":"..."}`) |
| `sendHangup` | Tunnel wants to hang up | `callId`, `hangupType` |
| `sendBusy` | Line is busy | `callId` |
| `stateChange` | Call state transition | `state`, `reason` (optional) |
| `error` | Something went wrong | `message` |

Opaque blobs and identity keys are base64-encoded. ICE servers use the format:

```json
{"urls":["turn:example.com"],"username":"u","password":"p"}
```

---

## Startup Sequence

```
signal-cli                          signal-call-tunnel
    |                                       |
    |-- spawn process ------------------>   |
    |-- config JSON + newline on stdin ---->|
    |                                       | parse config
    |                                       | initialize audio
    |                                       |
    |<-------- ready (on stdout) -----------|
    |   {"type":"ready",                    |
    |    "inputDeviceName":"...",           |
    |    "outputDeviceName":"..."}          |
    |                                       |
    |-- control messages on stdin --------->|
    |<-- control events on stdout ----------|
```

---

## Call Flows

### Outgoing call

```
signal-cli            signal-call-tunnel           Remote Phone
  |                          |                          |
  |-- spawn + config ------->|                          |
  |<-- ready ----------------|                          |
  |-- createOutgoingCall --->|                          |
  |-- proceed (TURN) ------->|                          |
  |                          | create offer             |
  |<-- sendOffer ------------|                          |
  |-- offer via Signal -------------------------------->|
  |<-- answer via Signal -------------------------------|
  |-- receivedAnswer ------->| (+ identity keys)        |
  |<-- sendIce --------------|                          |
  |-- ICE via Signal ------------------------------->   |
  |<-- ICE via Signal --------------------------------  |
  |-- receivedIce ---------->|                          |
  |                          | ICE connects             |
  |<-- stateChange:Connected |                          |
```

### Incoming call

```
signal-cli            signal-call-tunnel           Remote Phone
  |                          |                          |
  |<-- offer via Signal --------------------------------|
  |-- spawn + config ------->|                          |
  |<-- ready ----------------|                          |
  |-- receivedOffer -------->| (+ identity keys)        |
  |-- proceed (TURN) ------->|                          |
  |                          | process offer            |
  |<-- sendAnswer -----------|                          |
  |-- answer via Signal -------------------------------->|
  |<-- sendIce --------------|                          |
  |-- ICE via Signal ------------------------------>    |
  |<-- ICE via Signal --------------------------------  |
  |-- receivedIce ---------->|                          |
  |                          | ICE connecting...         |
  |                          |                          |
  |   (user accepts call)    |                          |
  |   Java defers accept     |                          |
  |                          |                          |
  |<-- stateChange:Ringing --|  (tunnel ready to accept)|
  |-- accept --------------->|  (deferred accept sent)  |
  |                          | accept                   |
  |<-- stateChange:Connected |                          |
```

### JSON-RPC client perspective

An external application (bot, UI, test script) interacts via JSON-RPC only.
It never touches the control socket directly.

**Important:** Call event notifications are not sent by default. Clients must
call `subscribeCallEvents` before initiating or receiving calls. Without this,
incoming calls are silently ignored (no tunnel is spawned).

```
JSON-RPC Client                    signal-cli daemon
  |                                      |
  |-- subscribeCallEvents() ------------>|  (required: enables call support)
  |                                      |
  |-- startCall(recipient) ------------->|
  |<-- {callId, state,                  -|
  |     inputDeviceName,                 |
  |     outputDeviceName}                |
  |                                      |
  |<-- callEvent: RINGING_OUTGOING ------|
  |   ... remote answers ...             |
  |<-- callEvent: CONNECTED -------------|
  |                                      |
  |   connect to audio devices           |
  |   (via platform audio APIs)          |
  |                                      |
  |-- hangupCall(callId) --------------->|  (or: receive callEvent ENDED)
  |<-- callEvent: ENDED -----------------|
  |   disconnect from audio devices      |
```

For incoming calls:

```
JSON-RPC Client                    signal-cli daemon
  |                                      |
  |-- subscribeCallEvents() ------------>|  (if not already subscribed)
  |                                      |
  |<-- callEvent: RINGING_INCOMING ------|  (includes callId, device names)
  |                                      |
  |-- acceptCall(callId) --------------->|
  |<-- {callId, state,                  -|
  |     inputDeviceName,                 |
  |     outputDeviceName}                |
  |                                      |
  |<-- callEvent: CONNECTING ------------|
  |<-- callEvent: CONNECTED -------------|
  |                                      |
  |   connect to audio devices           |
  |   (via platform audio APIs)          |
```

To stop receiving call events, call `unsubscribeCallEvents`.

---

## State Machine

Call states as seen by JSON-RPC clients:

```
                 startCall()
                     |
                     v
    +----- RINGING_OUTGOING ----+      RINGING_INCOMING -----+
    |              |            |              |              |
    | (timeout     | (answered) | (rejected)   | acceptCall() | (timeout
    |  ~60s)       |            |              |              |  ~60s)
    v              v            v              v              v
  ENDED        CONNECTED      ENDED       CONNECTING        ENDED
                 |                           |
                 |                           v
                 |                       CONNECTED
                 |                           |
                 | (hangup/error)            | (hangup/error)
                 v                           v
               ENDED                       ENDED
```

For outgoing calls, `CONNECTED` fires directly when the tunnel reports
`Connected` state -- there is no intermediate `CONNECTING` event.

For incoming calls, `CONNECTING` is set by Java when the user calls
`acceptCall()`, before the tunnel completes ICE negotiation.

Both directions have a 60-second ring timeout.

Reconnection (ICE restart):

```
  CONNECTED --> RECONNECTING --> CONNECTED  (ICE restart succeeded)
                     |
                     v
                   ENDED  (ICE restart failed)
```

`RECONNECTING` maps from the tunnel's `Connecting` state, which is emitted
during ICE restarts (not during initial connection).

---

## CallManager.java

`lib/src/main/java/org/asamk/signal/manager/helper/CallManager.java`

Manages the call lifecycle from the Java side:

1. Spawns `signal-call-tunnel` and writes config JSON to stdin
2. Keeps stdin open as the control write channel; reads stdout for control events
3. Captures stderr for tunnel logging
4. Parses `inputDeviceName` and `outputDeviceName` from the tunnel's `ready`
   message and includes them in `CallInfo`
5. Translates tunnel state changes into `CallInfo.State` values and fires
   `callEvent` JSON-RPC notifications to connected clients
6. Defers the `accept` message for incoming calls until the tunnel reports
   `Ringing` state (sending earlier causes the tunnel to drop it)
7. Schedules a 60-second ring timeout for both incoming and outgoing calls
8. On hangup: sends hangup message, closes stdin, and destroys the process

---

## Implementation Notes

### Peer ID consistency

The `peerId` field in `createOutgoingCall` and `receivedOffer` must be the actual
remote peer UUID (e.g., `senderAddress.toString()`). The tunnel rejects ICE
candidates if the peer ID doesn't match across calls, causing "Ignoring
peer-reflexive ICE candidate because the ufrag is unknown."

### sendHangup semantics

`sendHangup` from the tunnel is a request to send a hangup message via Signal
protocol. It is **not** a local state change -- local state transitions come
exclusively from `stateChange` events. For single-device clients, ignore
`AcceptedOnAnotherDevice`, `DeclinedOnAnotherDevice`, and
`BusyOnAnotherDevice` hangup types in the `hangupType` field -- sending these to
the remote peer causes it to terminate the call prematurely.

### Call ID serialization

Call IDs can exceed `Long.MAX_VALUE` in Java. Use `Long.toUnsignedString()` when
serializing to JSON for the tunnel (which expects unsigned 64-bit integers). In
the config JSON, `call_id` should also use unsigned representation.

### Incoming hangup filtering

When receiving hangup messages via Signal protocol, only honor `NORMAL` type
hangups. `ACCEPTED`, `DECLINED`, and `BUSY` types are multi-device coordination
messages and should be ignored by single-device clients.

### JSON-RPC call ID types

JSON-RPC clients may send call IDs as various numeric types (Long, BigInteger,
Integer). Use `Number.longValue()` rather than direct casting when extracting
call IDs from JSON-RPC parameters.

### Identity key format

Identity keys in `senderIdentityKey` and `receiverIdentityKey` must be **raw
32-byte Curve25519 public keys** (without the 0x05 DJB type prefix). If the
33-byte serialized form is used instead, SRTP key derivation produces different
keys on each side, causing authentication failures.

