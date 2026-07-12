package org.asamk.signal.http;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for the SSE initial-flush bug:
 * HttpServerHandler used to flush the initial SSE response only after a later
 * write in the 15-second keep-alive loop, meaning the HTTP response headers
 * were not flushed to the client until then.
 * Clients with a shorter connection timeout (e.g. 10 s) would time out before
 * receiving the initial response.
 *
 * This test verifies that the endpoint returns HTTP 200 within 2 seconds of
 * connecting to GET /api/v1/events.
 */
class SseInitialFlushTest {

    private HttpServerHandler handler;
    private int port;

    /** Finds a free local port. */
    private static int freePort() throws Exception {
        try (var ss = new ServerSocket(0)) {
            ss.setReuseAddress(true);
            return ss.getLocalPort();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        port = freePort();
        final Manager manager = ManagerMock.create("+10000000000");
        handler = new HttpServerHandler(new InetSocketAddress("127.0.0.1", port), manager);
        handler.init();
    }

    @AfterEach
    void tearDown() {
        if (handler != null) {
            handler.close();
        }
    }

    /**
     * The SSE endpoint MUST flush the initial HTTP response immediately upon
     * connection, before the 15-second keep-alive loop fires. A read timeout of
     * 2 000 ms is used — well below the 15-second wait interval but generous
     * enough to survive any CI scheduling jitter.
     */
    @Test
    void sseEndpointReturnsHeadersWithinTwoSeconds() {
        assertDoesNotThrow(() -> {
            var url = new URI("http", null, "127.0.0.1", port, "/api/v1/events", null, null).toURL();
            var conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setReadTimeout(2_000);   // 2 s — fails before fix (15 s flush), passes after
            conn.setConnectTimeout(2_000);
            try {
                conn.connect();
                assertEquals(200, conn.getResponseCode());
            } finally {
                conn.disconnect();
            }
        }, "SSE endpoint did not return the initial response within 2 seconds");
    }

}
