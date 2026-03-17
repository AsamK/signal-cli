package org.asamk.signal.jsonrpc;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.api.*;
import org.asamk.signal.output.JsonWriter;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the subscribeCallEvents / unsubscribeCallEvents JSON-RPC commands
 * introduced in commit d1e93dd.
 */
class SubscribeCallEventsTest {

    /**
     * Feeds pre-configured JSON-RPC lines to the handler, then returns null to end.
     */
    private static class LineFeeder {
        private final Queue<String> lines = new ConcurrentLinkedQueue<>();

        void addLine(String line) {
            lines.add(line);
        }

        String getLine() {
            return lines.poll();
        }
    }

    /**
     * Captures JSON-RPC responses written by the handler.
     */
    private static class CapturingJsonWriter implements JsonWriter {
        final List<Object> written = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void write(final Object object) {
            written.add(object);
        }
    }

    /**
     * Minimal Manager stub that tracks call event listener add/remove calls.
     */
    private static class StubManager implements Manager {
        final List<CallEventListener> listeners = new ArrayList<>();
        final AtomicInteger addCount = new AtomicInteger(0);
        final AtomicInteger removeCount = new AtomicInteger(0);
        final String selfNumber;

        StubManager(String selfNumber) {
            this.selfNumber = selfNumber;
        }

        @Override public void addCallEventListener(CallEventListener listener) {
            addCount.incrementAndGet();
            listeners.add(listener);
        }

        @Override public void removeCallEventListener(CallEventListener listener) {
            removeCount.incrementAndGet();
            listeners.remove(listener);
        }

        @Override public String getSelfNumber() { return selfNumber; }

        // --- Stubs for remaining Manager interface methods ---
        @Override public Map<String, UserStatus> getUserStatus(Set<String> n) { return Map.of(); }
        @Override public Map<String, UsernameStatus> getUsernameStatus(Set<String> u) { return Map.of(); }
        @Override public void updateAccountAttributes(String d, Boolean u, Boolean dn, Boolean ns) {}
        @Override public Configuration getConfiguration() { return null; }
        @Override public void updateConfiguration(Configuration c) {}
        @Override public void updateProfile(UpdateProfile u) {}
        @Override public String getUsername() { return null; }
        @Override public UsernameLinkUrl getUsernameLink() { return null; }
        @Override public void setUsername(String u) {}
        @Override public void deleteUsername() {}
        @Override public void startChangeNumber(String n, boolean v, String c) {}
        @Override public void finishChangeNumber(String n, String v, String p) {}
        @Override public void unregister() {}
        @Override public void deleteAccount() {}
        @Override public void submitRateLimitRecaptchaChallenge(String c, String cap) {}
        @Override public List<Device> getLinkedDevices() { return List.of(); }
        @Override public void updateLinkedDevice(int d, String n) {}
        @Override public void removeLinkedDevices(int d) {}
        @Override public void addDeviceLink(DeviceLinkUrl u) {}
        @Override public void setRegistrationLockPin(Optional<String> p) {}
        @Override public List<Group> getGroups() { return List.of(); }
        @Override public List<Group> getGroups(Collection<GroupId> g) { return List.of(); }
        @Override public SendGroupMessageResults quitGroup(GroupId g, Set<RecipientIdentifier.Single> a) { return null; }
        @Override public void deleteGroup(GroupId g) {}
        @Override public Pair<GroupId, SendGroupMessageResults> createGroup(String n, Set<RecipientIdentifier.Single> m, String a) { return null; }
        @Override public SendGroupMessageResults updateGroup(GroupId g, UpdateGroup u) { return null; }
        @Override public Pair<GroupId, SendGroupMessageResults> joinGroup(GroupInviteLinkUrl u) { return null; }
        @Override public SendMessageResults sendTypingMessage(TypingAction a, Set<RecipientIdentifier> r) { return null; }
        @Override public SendMessageResults sendReadReceipt(RecipientIdentifier.Single s, List<Long> m) { return null; }
        @Override public SendMessageResults sendViewedReceipt(RecipientIdentifier.Single s, List<Long> m) { return null; }
        @Override public SendMessageResults sendMessage(Message m, Set<RecipientIdentifier> r, boolean n) { return null; }
        @Override public SendMessageResults sendEditMessage(Message m, Set<RecipientIdentifier> r, long t) { return null; }
        @Override public SendMessageResults sendRemoteDeleteMessage(long t, Set<RecipientIdentifier> r) { return null; }
        @Override public SendMessageResults sendMessageReaction(String e, boolean rm, RecipientIdentifier.Single a, long t, Set<RecipientIdentifier> r, boolean n, boolean s) { return null; }
        @Override public SendMessageResults sendAdminDelete(RecipientIdentifier.Single a, long t, Set<RecipientIdentifier.Group> r, boolean n, boolean s) { return null; }
        @Override public SendMessageResults sendPinMessage(int d, RecipientIdentifier.Single a, long t, Set<RecipientIdentifier> r, boolean n, boolean s) { return null; }
        @Override public SendMessageResults sendUnpinMessage(RecipientIdentifier.Single a, long t, Set<RecipientIdentifier> r, boolean n, boolean s) { return null; }
        @Override public SendMessageResults sendPaymentNotificationMessage(byte[] r, String n, RecipientIdentifier.Single re) { return null; }
        @Override public SendMessageResults sendEndSessionMessage(Set<RecipientIdentifier.Single> r) { return null; }
        @Override public SendMessageResults sendMessageRequestResponse(MessageEnvelope.Sync.MessageRequestResponse.Type t, Set<RecipientIdentifier> r) { return null; }
        @Override public SendMessageResults sendPollCreateMessage(String q, boolean a, List<String> o, Set<RecipientIdentifier> r, boolean n) { return null; }
        @Override public SendMessageResults sendPollVoteMessage(RecipientIdentifier.Single a, long t, List<Integer> o, int v, Set<RecipientIdentifier> r, boolean n) { return null; }
        @Override public SendMessageResults sendPollTerminateMessage(long t, Set<RecipientIdentifier> r, boolean n) { return null; }
        @Override public void hideRecipient(RecipientIdentifier.Single r) {}
        @Override public void deleteRecipient(RecipientIdentifier.Single r) {}
        @Override public void deleteContact(RecipientIdentifier.Single r) {}
        @Override public void setContactName(RecipientIdentifier.Single r, String g, String f, String ng, String nf, String n) {}
        @Override public void setContactsBlocked(Collection<RecipientIdentifier.Single> r, boolean b) {}
        @Override public void setGroupsBlocked(Collection<GroupId> g, boolean b) {}
        @Override public void setExpirationTimer(RecipientIdentifier.Single r, int t) {}
        @Override public StickerPackUrl uploadStickerPack(File p) { return null; }
        @Override public void installStickerPack(StickerPackUrl u) {}
        @Override public List<StickerPack> getStickerPacks() { return List.of(); }
        @Override public void requestAllSyncData() {}
        @Override public void addReceiveHandler(ReceiveMessageHandler h, boolean w) {}
        @Override public void removeReceiveHandler(ReceiveMessageHandler h) {}
        @Override public boolean isReceiving() { return false; }
        @Override public void receiveMessages(Optional<Duration> t, Optional<Integer> m, ReceiveMessageHandler h) {}
        @Override public void stopReceiveMessages() {}
        @Override public void setReceiveConfig(ReceiveConfig r) {}
        @Override public boolean isContactBlocked(RecipientIdentifier.Single r) { return false; }
        @Override public void sendContacts() {}
        @Override public List<Recipient> getRecipients(boolean o, Optional<Boolean> b, Collection<RecipientIdentifier.Single> a, Optional<String> n) { return List.of(); }
        @Override public String getContactOrProfileName(RecipientIdentifier.Single r) { return null; }
        @Override public Group getGroup(GroupId g) { return null; }
        @Override public List<Identity> getIdentities() { return List.of(); }
        @Override public List<Identity> getIdentities(RecipientIdentifier.Single r) { return List.of(); }
        @Override public boolean trustIdentityVerified(RecipientIdentifier.Single r, IdentityVerificationCode v) { return false; }
        @Override public boolean trustIdentityAllKeys(RecipientIdentifier.Single r) { return false; }
        @Override public void addAddressChangedListener(Runnable l) {}
        @Override public void addClosedListener(Runnable l) {}
        @Override public InputStream retrieveAttachment(String id) { return null; }
        @Override public InputStream retrieveContactAvatar(RecipientIdentifier.Single r) { return null; }
        @Override public InputStream retrieveProfileAvatar(RecipientIdentifier.Single r) { return null; }
        @Override public InputStream retrieveGroupAvatar(GroupId g) { return null; }
        @Override public InputStream retrieveSticker(StickerPackId s, int i) { return null; }
        @Override public CallInfo startCall(RecipientIdentifier.Single r) { return null; }
        @Override public CallInfo acceptCall(long c) { return null; }
        @Override public void hangupCall(long c) {}
        @Override public void rejectCall(long c) {}
        @Override public List<CallInfo> listActiveCalls() { return List.of(); }
        @Override public void sendCallOffer(RecipientIdentifier.Single r, CallOffer o) {}
        @Override public void sendCallAnswer(RecipientIdentifier.Single r, long c, byte[] a) {}
        @Override public void sendIceUpdate(RecipientIdentifier.Single r, long c, List<byte[]> i) {}
        @Override public void sendHangup(RecipientIdentifier.Single r, long c, MessageEnvelope.Call.Hangup.Type t) {}
        @Override public void sendBusy(RecipientIdentifier.Single r, long c) {}
        @Override public List<TurnServer> getTurnServerInfo() { return List.of(); }
        @Override public void close() {}
    }

    /**
     * Minimal MultiAccountManager stub for multi-account mode tests.
     */
    private static class StubMultiAccountManager implements MultiAccountManager {
        final List<Manager> managers;
        final List<Consumer<Manager>> addedHandlers = new ArrayList<>();

        StubMultiAccountManager(List<Manager> managers) {
            this.managers = new ArrayList<>(managers);
        }

        @Override public List<String> getAccountNumbers() {
            return managers.stream().map(Manager::getSelfNumber).toList();
        }

        @Override public List<Manager> getManagers() { return managers; }

        @Override public void addOnManagerAddedHandler(Consumer<Manager> handler) {
            addedHandlers.add(handler);
        }

        @Override public void addOnManagerRemovedHandler(Consumer<Manager> handler) {}

        @Override public Manager getManager(String phoneNumber) {
            return managers.stream().filter(m -> phoneNumber.equals(m.getSelfNumber())).findFirst().orElse(null);
        }

        @Override public URI getNewProvisioningDeviceLinkUri() { return null; }
        @Override public ProvisioningManager getProvisioningManagerFor(URI u) { return null; }
        @Override public RegistrationManager getNewRegistrationManager(String a) { return null; }
        @Override public void close() {}
    }

    private static String jsonRpcCall(int id, String method) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}";
    }

    // --- Single-account mode tests ---

    @Test
    void callEventsNotSubscribedByDefault() {
        var manager = new StubManager("+15551234567");
        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        // Send no subscribeCallEvents, just end the connection
        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(manager);

        // No listeners should have been added
        assertEquals(0, manager.addCount.get(), "call events should not be auto-subscribed");
    }

    @Test
    void subscribeCallEventsAddsListener() {
        var manager = new StubManager("+15551234567");
        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "subscribeCallEvents"));
        // null terminates the read loop

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(manager);

        assertEquals(1, manager.addCount.get(), "subscribeCallEvents should add one listener");
        // Cleanup in finally block should remove it
        assertEquals(1, manager.removeCount.get(), "cleanup should remove the listener");
        assertEquals(0, manager.listeners.size(), "no listeners should remain after cleanup");
    }

    @Test
    void subscribeCallEventsIsIdempotent() {
        var manager = new StubManager("+15551234567");
        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "subscribeCallEvents"));
        feeder.addLine(jsonRpcCall(2, "subscribeCallEvents"));

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(manager);

        // Idempotent guard: second call should not add another listener
        assertEquals(1, manager.addCount.get(), "duplicate subscribeCallEvents should be ignored");
    }

    @Test
    void unsubscribeCallEventsRemovesListener() {
        var manager = new StubManager("+15551234567");
        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "subscribeCallEvents"));
        feeder.addLine(jsonRpcCall(2, "unsubscribeCallEvents"));

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(manager);

        assertEquals(1, manager.addCount.get(), "should have subscribed once");
        // removeCount: 1 from explicit unsubscribe. The finally block's unsubscribeAllCallEvents
        // iterates an empty list so adds 0 more.
        assertEquals(1, manager.removeCount.get(), "should have unsubscribed once");
        assertEquals(0, manager.listeners.size());
    }

    @Test
    void unsubscribeWithoutSubscribeIsNoOp() {
        var manager = new StubManager("+15551234567");
        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "unsubscribeCallEvents"));

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(manager);

        assertEquals(0, manager.addCount.get());
        assertEquals(0, manager.removeCount.get());
    }

    // --- Multi-account mode tests ---

    @Test
    void multiAccountSubscribeCallEventsSubscribesAllManagers() {
        var manager1 = new StubManager("+15551111111");
        var manager2 = new StubManager("+15552222222");
        var multi = new StubMultiAccountManager(List.of(manager1, manager2));

        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "subscribeCallEvents"));

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(multi);

        assertEquals(1, manager1.addCount.get(), "manager1 should have one listener");
        assertEquals(1, manager2.addCount.get(), "manager2 should have one listener");
        // Also registers an onManagerAdded handler
        assertEquals(1, multi.addedHandlers.size(), "should register onManagerAdded handler");
    }

    @Test
    void multiAccountUnsubscribeCallEventsCleansUpAll() {
        var manager1 = new StubManager("+15551111111");
        var manager2 = new StubManager("+15552222222");
        var multi = new StubMultiAccountManager(List.of(manager1, manager2));

        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "subscribeCallEvents"));
        feeder.addLine(jsonRpcCall(2, "unsubscribeCallEvents"));

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(multi);

        assertEquals(1, manager1.addCount.get());
        assertEquals(1, manager2.addCount.get());
        assertEquals(1, manager1.removeCount.get(), "manager1 listener should be removed");
        assertEquals(1, manager2.removeCount.get(), "manager2 listener should be removed");
    }

    @Test
    void multiAccountCallEventsNotSubscribedByDefault() {
        var manager1 = new StubManager("+15551111111");
        var multi = new StubMultiAccountManager(List.of(manager1));

        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(multi);

        assertEquals(0, manager1.addCount.get(), "call events should not be auto-subscribed in multi mode");
    }
}
