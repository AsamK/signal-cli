package org.asamk.signal.util;

import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

import jdk.net.ExtendedSocketOptions;
import jdk.net.UnixDomainPrincipal;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

public class IOUtils {

    private final static Logger logger = LoggerFactory.getLogger(IOUtils.class);

    private IOUtils() {
    }

    public static Charset getConsoleCharset() {
        final var console = System.console();
        return console == null ? Charset.defaultCharset() : console.charset();
    }

    public static String readAll(InputStream in, Charset charset) throws IOException {
        var output = new StringWriter();
        var buffer = new byte[4096];
        int n;
        while (-1 != (n = in.read(buffer))) {
            output.write(new String(buffer, 0, n, charset));
        }
        return output.toString();
    }

    public static void createPrivateDirectories(File file) throws IOException {
        if (file.exists()) {
            return;
        }

        final var path = file.toPath();
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE);
            Files.createDirectories(path, PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(path);
        }
    }

    public static File getDataHomeDir() {
        var dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome != null) {
            return new File(dataHome);
        }

        logger.debug("XDG_DATA_HOME not set, falling back to home dir");
        return new File(new File(System.getProperty("user.home"), ".local"), "share");
    }

    public static File getRuntimeDir() {
        var runtimeDir = System.getenv("XDG_RUNTIME_DIR");
        if (runtimeDir != null) {
            return new File(runtimeDir);
        }

        logger.debug("XDG_RUNTIME_DIR not set, falling back to temp dir");
        return new File(System.getProperty("java.io.tmpdir"));
    }

    public static Supplier<String> getLineSupplier(final Reader reader) {
        final var bufferedReader = new BufferedReader(reader);
        return () -> {
            try {
                return bufferedReader.readLine();
            } catch (IOException e) {
                logger.error("Error occurred while reading line", e);
                return null;
            }
        };
    }

    public static InetSocketAddress parseInetSocketAddress(final String tcpAddress) throws UserErrorException {
        final var colonIndex = tcpAddress.lastIndexOf(':');
        if (colonIndex < 0) {
            throw new UserErrorException("Invalid tcp bind address (expected host:port): " + tcpAddress);
        }
        final var host = tcpAddress.substring(0, colonIndex);
        final var portString = tcpAddress.substring(colonIndex + 1);

        final int port;
        try {
            port = Integer.parseInt(portString);
        } catch (NumberFormatException e) {
            throw new UserErrorException("Invalid tcp port: " + portString, e);
        }
        final var socketAddress = new InetSocketAddress(host, port);
        if (socketAddress.isUnresolved()) {
            throw new UserErrorException("Invalid tcp bind address, invalid host: " + host);
        }
        return socketAddress;
    }

    public static String getUnixDomainPrincipal(final SocketChannel channel) throws IOException {
        UnixDomainPrincipal principal = null;
        try {
            principal = channel.getOption(ExtendedSocketOptions.SO_PEERCRED);
        } catch (UnsupportedOperationException | NoClassDefFoundError ignored) {
        }
        return principal == null ? null : principal.toString();
    }

    public static ServerSocketChannel bindSocket(final SocketAddress address) throws IOErrorException {
        final ServerSocketChannel serverChannel;
        try {
            preBind(address);
            serverChannel = address instanceof UnixDomainSocketAddress
                    ? ServerSocketChannel.open(StandardProtocolFamily.UNIX)
                    : ServerSocketChannel.open();
            serverChannel.bind(address);
            logger.info("Listening on socket: " + address);
            postBind(address);
        } catch (IOException e) {
            throw new IOErrorException("Failed to bind socket " + address + ": " + e.getMessage(), e);
        }
        return serverChannel;
    }

    private static void preBind(SocketAddress address) throws IOException {
        if (address instanceof UnixDomainSocketAddress usa) {
            createPrivateDirectories(usa.getPath().toFile().getParentFile());
        }
    }

    private static void postBind(SocketAddress address) {
        if (address instanceof UnixDomainSocketAddress usa) {
            usa.getPath().toFile().deleteOnExit();
        }
    }
}
