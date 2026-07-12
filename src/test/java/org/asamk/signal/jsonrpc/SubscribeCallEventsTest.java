package org.asamk.signal.jsonrpc;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private record ManagedManager(Manager manager, ManagerMock.State state) {
    }

    private static ManagedManager createManager(final String selfNumber) {
        final var state = new ManagerMock.State();
        return new ManagedManager(ManagerMock.create(selfNumber, state), state);
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

        @Override
        public List<String> getAccountNumbers() {
            return managers.stream().map(Manager::getSelfNumber).toList();
        }

        @Override
        public List<Manager> getManagers() {
            return managers;
        }

        @Override
        public void addOnManagerAddedHandler(Consumer<Manager> handler) {
            addedHandlers.add(handler);
        }

        @Override
        public void addOnManagerRemovedHandler(Consumer<Manager> handler) {
        }

        @Override
        public Manager getManager(String phoneNumber) {
            return managers.stream().filter(m -> phoneNumber.equals(m.getSelfNumber())).findFirst().orElse(null);
        }

        @Override
        public URI getNewProvisioningDeviceLinkUri() {
            return null;
        }

        @Override
        public ProvisioningManager getProvisioningManagerFor(URI u) {
            return null;
        }

        @Override
        public RegistrationManager getNewRegistrationManager(String a) {
            return null;
        }

        @Override
        public void close() {
        }
    }

    private static String jsonRpcCall(int id, String method) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}";
    }

    private static String jsonRpcCall(int id, String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params + "}";
    }

    // --- Single-account mode tests ---

    @Test
    void callEventsNotSubscribedByDefault() {
        var manager = createManager("+15551234567");
        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        // Send no subscribeCallEvents, just end the connection
        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(manager.manager());

        // No listeners should have been added
        assertEquals(0, manager.state().addCallEventListenerCount.get(), "call events should not be auto-subscribed");
    }

    @Test
    void subscribeCallEventsAddsListener() {
        var manager = createManager("+15551234567");
        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "subscribeCallEvents"));
        // null terminates the read loop

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(manager.manager());

        assertEquals(1, manager.state().addCallEventListenerCount.get(), "subscribeCallEvents should add one listener");
        // Cleanup in finally block should remove it
        assertEquals(1, manager.state().removeCallEventListenerCount.get(), "cleanup should remove the listener");
        assertEquals(0, manager.state().callEventListeners.size(), "no listeners should remain after cleanup");
    }

    @Test
    void subscribeCallEventsCanBeCalledMultipleTimes() {
        var manager = createManager("+15551234567");
        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "subscribeCallEvents"));
        feeder.addLine(jsonRpcCall(2, "subscribeCallEvents"));

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(manager.manager());

        // The implementation allows multiple subscriptions, so two calls add two listeners
        assertEquals(2, manager.state().addCallEventListenerCount.get(), "multiple subscribeCallEvents should add multiple listeners");
    }

    @Test
    void unsubscribeCallEventsRemovesListener() {
        var manager = createManager("+15551234567");
        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "subscribeCallEvents"));
        feeder.addLine(jsonRpcCall(2, "unsubscribeCallEvents", "{\"subscription\":0}"));

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(manager.manager());

        assertEquals(1, manager.state().addCallEventListenerCount.get(), "should have subscribed once");
        // removeCount: 1 from explicit unsubscribe. The finally block's unsubscribeAllCallEvents
        // iterates an empty list so adds 0 more.
        assertEquals(1, manager.state().removeCallEventListenerCount.get(), "should have unsubscribed once");
        assertEquals(0, manager.state().callEventListeners.size());
    }

    @Test
    void unsubscribeWithoutSubscribeIsNoOp() {
        var manager = createManager("+15551234567");
        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "unsubscribeCallEvents"));

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(manager.manager());

        assertEquals(0, manager.state().addCallEventListenerCount.get());
        assertEquals(0, manager.state().removeCallEventListenerCount.get());
    }

    // --- Multi-account mode tests ---

    @Test
    void multiAccountSubscribeCallEventsSubscribesAllManagers() {
        var manager1 = createManager("+15551111111");
        var manager2 = createManager("+15552222222");
        var multi = new StubMultiAccountManager(List.of(manager1.manager(), manager2.manager()));

        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "subscribeCallEvents"));

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(multi);

        assertEquals(1, manager1.state().addCallEventListenerCount.get(), "manager1 should have one listener");
        assertEquals(1, manager2.state().addCallEventListenerCount.get(), "manager2 should have one listener");
        // Also registers an onManagerAdded handler for receive and one for call events
        assertEquals(2, multi.addedHandlers.size(), "should register onManagerAdded handlers");
    }

    @Test
    void multiAccountUnsubscribeCallEventsCleansUpAll() {
        var manager1 = createManager("+15551111111");
        var manager2 = createManager("+15552222222");
        var multi = new StubMultiAccountManager(List.of(manager1.manager(), manager2.manager()));

        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        feeder.addLine(jsonRpcCall(1, "subscribeCallEvents"));
        feeder.addLine(jsonRpcCall(2, "unsubscribeCallEvents", "{\"subscription\":0}"));

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(multi);

        assertEquals(1, manager1.state().addCallEventListenerCount.get());
        assertEquals(1, manager2.state().addCallEventListenerCount.get());
        assertEquals(1, manager1.state().removeCallEventListenerCount.get(), "manager1 listener should be removed");
        assertEquals(1, manager2.state().removeCallEventListenerCount.get(), "manager2 listener should be removed");
    }

    @Test
    void multiAccountCallEventsNotSubscribedByDefault() {
        var manager1 = createManager("+15551111111");
        var multi = new StubMultiAccountManager(List.of(manager1.manager()));

        var feeder = new LineFeeder();
        var writer = new CapturingJsonWriter();

        var handler = new SignalJsonRpcDispatcherHandler(writer, feeder::getLine, true);
        handler.handleConnection(multi);

        assertEquals(0, manager1.state().addCallEventListenerCount.get(), "call events should not be auto-subscribed in multi mode");
    }
}
