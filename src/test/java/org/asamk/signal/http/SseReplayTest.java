package org.asamk.signal.http;

import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the event ids, the replay of missed events for a client that reconnects with a
 * Last-Event-ID header, and the configurable keep-alive interval of GET /api/v1/events.
 */
class SseReplayTest {

    private final ManagerMock.State state = new ManagerMock.State();
    private HttpServerHandler handler;
    private int port;

    private void start(final Duration keepAliveInterval) throws Exception {
        try (var ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        handler = new HttpServerHandler(new InetSocketAddress("127.0.0.1", port),
                keepAliveInterval,
                ManagerMock.create("+10000000000", state));
        handler.init();
    }

    @AfterEach
    void tearDown() {
        if (handler != null) {
            handler.close();
        }
    }

    @Test
    void eventsHaveIds() throws Exception {
        start(Duration.ofSeconds(15));
        try (var client = connect(null, 2)) {
            receive("one");

            final var event = client.next();
            assertNotNull(event.id());
            assertTrue(event.data().contains("\"one\""));
        }
    }

    @Test
    void reconnectWithLastEventIdReplaysMissedEvents() throws Exception {
        start(Duration.ofSeconds(15));
        final String firstId;
        try (var client = connect(null, 2)) {
            receive("one");
            receive("two");
            firstId = client.next().id();
            client.next();
        }
        receive("three");

        try (var client = connect(firstId, 3)) {
            assertTrue(client.next().data().contains("\"two\""));
            assertTrue(client.next().data().contains("\"three\""));

            receive("four");
            assertTrue(client.next().data().contains("\"four\""));
        }
    }

    @Test
    void unknownLastEventIdReplaysAllBufferedEvents() throws Exception {
        start(Duration.ofSeconds(15));
        try (var client = connect(null, 2)) {
            receive("one");
            receive("two");
            client.next();
            client.next();
        }

        try (var client = connect("0-1", 3)) {
            assertTrue(client.next().data().contains("\"one\""));
            assertTrue(client.next().data().contains("\"two\""));
        }
    }

    @Test
    void connectWithoutLastEventIdReplaysNothing() throws Exception {
        start(Duration.ofSeconds(15));
        try (var client = connect(null, 2)) {
            receive("one");
            client.next();
        }

        try (var client = connect(null, 3)) {
            receive("two");
            assertTrue(client.next().data().contains("\"two\""));
        }
    }

    @Test
    void keepAliveIntervalIsConfigurable() throws Exception {
        start(Duration.ofSeconds(1));
        try (var client = connect(null, 2)) {
            // The read timeout is 5 s, the default interval of 15 s would fail here
            assertEquals(":", client.nextLine());
        }
    }

    /**
     * Passes a message to the registered receive handlers, like the manager's receive thread does.
     */
    private void receive(final String message) {
        final var exception = new RuntimeException(message);
        for (final var receiveHandler : state.receiveHandlers) {
            receiveHandler.handleMessage(null, exception);
        }
    }

    /**
     * Connects and waits until the server has registered the client, i.e. until the manager has
     * seen the given total number of addReceiveHandler calls.
     */
    private SseClient connect(final String lastEventId, final int addReceiveHandlerCount) throws Exception {
        final var client = new SseClient(port, lastEventId);
        final var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (state.addReceiveHandlerCount.get() < addReceiveHandlerCount) {
            if (System.nanoTime() > deadline) {
                client.close();
                throw new AssertionError("Client was not registered in time");
            }
            Thread.sleep(10);
        }
        return client;
    }

    private record Event(String id, String data) {}

    private static final class SseClient implements AutoCloseable {

        private final HttpURLConnection connection;
        private final BufferedReader reader;

        SseClient(final int port, final String lastEventId) throws IOException {
            final var url = URI.create("http://127.0.0.1:" + port + "/api/v1/events").toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "text/event-stream");
            if (lastEventId != null) {
                connection.setRequestProperty("Last-Event-ID", lastEventId);
            }
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(5_000);
            assertEquals(200, connection.getResponseCode());
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        }

        String nextLine() throws IOException {
            final var line = reader.readLine();
            if (line == null) {
                throw new EOFException();
            }
            return line;
        }

        /**
         * Reads the next event, skipping keep-alive comments.
         */
        Event next() throws IOException {
            String id = null;
            String data = null;
            while (true) {
                final var line = nextLine();
                if (line.isEmpty()) {
                    if (data != null) {
                        return new Event(id, data);
                    }
                } else if (line.startsWith("id:")) {
                    id = line.substring("id:".length());
                } else if (line.startsWith("data:")) {
                    data = line.substring("data:".length());
                }
            }
        }

        @Override
        public void close() {
            connection.disconnect();
        }
    }
}
