package org.asamk.signal.http;

import org.asamk.signal.manager.internal.MultiAccountManagerImpl;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The account query parameter of GET /api/v1/events accepts the same account identifiers as the JSON-RPC
 * account parameter.
 */
class SseAccountParameterTest {

    private HttpServerHandler handler;
    private int port;

    @AfterEach
    void tearDown() {
        if (handler != null) {
            handler.close();
        }
    }

    @Test
    void acceptsAciWithoutDashes() throws Exception {
        start();
        // ManagerMock's ACI is 00000000-0000-0000-0000-000000000000
        assertEquals(200, connect("00000000000000000000000000000000"));
    }

    @Test
    void acceptsAciWithDashes() throws Exception {
        start();
        assertEquals(200, connect("00000000-0000-0000-0000-000000000000"));
    }

    private void start() throws Exception {
        try (var ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        handler = new HttpServerHandler(new InetSocketAddress("127.0.0.1", port),
                Duration.ofSeconds(15),
                new MultiAccountManagerImpl(List.of(ManagerMock.create("+10000000000")), null));
        handler.init();
    }

    private int connect(final String account) throws Exception {
        final var url = URI.create("http://127.0.0.1:" + port + "/api/v1/events?account=" + account).toURL();
        final var connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(5_000);
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }
}
