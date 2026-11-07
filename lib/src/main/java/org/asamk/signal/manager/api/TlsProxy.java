package org.asamk.signal.manager.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * A Signal TLS proxy, as used by the official Signal apps.
 */
public record TlsProxy(String host, int port) {

    private static final int DEFAULT_PORT = 443;

    /**
     * Parses a proxy given as "HOST", "HOST:PORT" (default port 443) or as a proxy link like the ones shared for
     * the Signal apps ("https://signal.tube/#HOST" or "sgnl://signal.tube/#HOST").
     *
     * @throws InvalidTlsProxyException If the value is not a valid proxy.
     */
    public static TlsProxy parse(String value) throws InvalidTlsProxyException {
        var address = value.strip();
        if (address.contains("://")) {
            final URI uri;
            try {
                uri = new URI(address);
            } catch (URISyntaxException e) {
                throw new InvalidTlsProxyException("Invalid TLS proxy link: " + value, e);
            }
            final var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("https") || scheme.equals("sgnl")) || !"signal.tube".equalsIgnoreCase(uri.getHost())) {
                throw new InvalidTlsProxyException("Invalid TLS proxy link (expected https://signal.tube/#HOST): "
                        + value);
            }
            address = uri.getFragment() == null ? "" : uri.getFragment();
        }

        final var colonIndex = address.lastIndexOf(':');
        final var host = colonIndex < 0 ? address : address.substring(0, colonIndex);
        final int port;
        if (colonIndex < 0) {
            port = DEFAULT_PORT;
        } else {
            final var portString = address.substring(colonIndex + 1);
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                throw new InvalidTlsProxyException("Invalid TLS proxy port: " + portString, e);
            }
            if (port < 1 || port > 65535) {
                throw new InvalidTlsProxyException("Invalid TLS proxy port: " + portString);
            }
        }
        if (!host.matches("[A-Za-z0-9.-]+") || host.startsWith(".") || host.endsWith(".")) {
            throw new InvalidTlsProxyException("Invalid TLS proxy host: " + host);
        }
        return new TlsProxy(host, port);
    }

    public static final class InvalidTlsProxyException extends Exception {

        public InvalidTlsProxyException(String message) {
            super(message);
        }

        public InvalidTlsProxyException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
