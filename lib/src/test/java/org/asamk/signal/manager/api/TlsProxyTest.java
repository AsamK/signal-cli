package org.asamk.signal.manager.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TlsProxyTest {

    @Test
    void parsesHostWithDefaultPort() throws TlsProxy.InvalidTlsProxyException {
        assertEquals(new TlsProxy("proxy.example.org", 443), TlsProxy.parse("proxy.example.org"));
    }

    @Test
    void parsesHostAndPort() throws TlsProxy.InvalidTlsProxyException {
        assertEquals(new TlsProxy("proxy.example.org", 8443), TlsProxy.parse("proxy.example.org:8443"));
    }

    @Test
    void parsesProxyLinks() throws TlsProxy.InvalidTlsProxyException {
        assertEquals(new TlsProxy("proxy.example.org", 443), TlsProxy.parse("https://signal.tube/#proxy.example.org"));
        assertEquals(new TlsProxy("proxy.example.org", 443), TlsProxy.parse("sgnl://signal.tube/#proxy.example.org"));
    }

    @Test
    void rejectsInvalidValues() {
        for (final var value : new String[]{
                "",
                "proxy.example.org:",
                "proxy.example.org:70000",
                "https://example.org/#proxy.example.org",
                "https://signal.tube/",
                "user@proxy.example.org"
        }) {
            assertThrows(TlsProxy.InvalidTlsProxyException.class, () -> TlsProxy.parse(value), value);
        }
    }
}
