package org.asamk.signal.manager.config;

import org.junit.jupiter.api.Test;
import org.signal.network.config.SignalProxy;

import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ServiceConfigTest {

    @Test
    void resolvesOnlyTheTlsProxyHost() throws UnknownHostException {
        final var dns = ServiceConfig.dnsBehindTlsProxy(new SignalProxy("localhost", 443));

        assertFalse(dns.lookup("localhost").isEmpty());

        final var addresses = dns.lookup("chat.signal.org");
        assertEquals(1, addresses.size());
        assertEquals("chat.signal.org", addresses.getFirst().getHostName());
        assertEquals("127.0.0.1", addresses.getFirst().getHostAddress());
    }
}
