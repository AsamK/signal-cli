package org.asamk.signal.jsonrpc;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.JsonWriterImpl;
import org.asamk.signal.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SocketHandler implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SocketHandler.class);
    private static final AtomicInteger threadNumber = new AtomicInteger(0);

    private final ServerSocketChannel serverChannel;

    private Thread listenerThread;
    private final List<AutoCloseable> channels = new ArrayList<>();
    private final Consumer<SocketChannel> socketHandler;
    private final boolean noReceiveOnStart;

    public SocketHandler(final ServerSocketChannel serverChannel, final Manager m, final boolean noReceiveOnStart) {
        this.serverChannel = serverChannel;
        this.socketHandler = channel -> getSignalJsonRpcDispatcherHandler(channel).handleConnection(m);
        this.noReceiveOnStart = noReceiveOnStart;
    }

    public SocketHandler(
            final ServerSocketChannel serverChannel, final MultiAccountManager c, final boolean noReceiveOnStart
    ) {
        this.serverChannel = serverChannel;
        this.socketHandler = channel -> getSignalJsonRpcDispatcherHandler(channel).handleConnection(c);
        this.noReceiveOnStart = noReceiveOnStart;
    }

    public void init() {
        if (listenerThread != null) {
            throw new AssertionError("SocketHandler already initialized");
        }
        SocketAddress socketAddress;
        try {
            socketAddress = serverChannel.getLocalAddress();
        } catch (IOException e) {
            logger.debug("Failed to get socket address: {}", e.getMessage());
            socketAddress = null;
        }
        final var address = socketAddress == null ? "<Unknown socket address>" : socketAddress;
        logger.debug("Starting JSON-RPC server on {}", address);

        listenerThread = Thread.ofPlatform().name("daemon-listener").start(() -> {
            try (final var executor = Executors.newCachedThreadPool()) {
                logger.info("Started JSON-RPC server on {}", address);
                while (true) {
                    final var connectionId = threadNumber.getAndIncrement();
                    final SocketChannel channel;
                    final String clientString;
                    try {
                        channel = serverChannel.accept();
                        clientString = channel.getRemoteAddress() + " " + IOUtils.getUnixDomainPrincipal(channel);
                        logger.info("Accepted new client connection {}: {}", connectionId, clientString);
                    } catch (ClosedChannelException ignored) {
                        logger.trace("Listening socket has been closed");
                        break;
                    } catch (IOException e) {
                        logger.error("Failed to accept new socket connection", e);
                        break;
                    }
                    channels.add(channel);
                    executor.submit(() -> {
                        try (final var c = channel) {
                            socketHandler.accept(c);
                        } catch (IOException e) {
                            logger.warn("Failed to close channel", e);
                        } catch (Throwable e) {
                            logger.warn("Connection handler failed, closing connection", e);
                        }
                        logger.info("Connection {} closed: {}", connectionId, clientString);
                        channels.remove(channel);
                    });
                }
            }
        });
    }

    @Override
    public void close() throws Exception {
        if (listenerThread == null) {
            return;
        }
        serverChannel.close();
        for (final var c : new ArrayList<>(channels)) {
            c.close();
        }
        listenerThread.join();
        channels.clear();
        listenerThread = null;
    }

    private SignalJsonRpcDispatcherHandler getSignalJsonRpcDispatcherHandler(final SocketChannel c) {
        final var lineSupplier = IOUtils.getLineSupplier(Channels.newReader(c, StandardCharsets.UTF_8));
        final var jsonOutputWriter = new JsonWriterImpl(Channels.newWriter(c, StandardCharsets.UTF_8));

        return new SignalJsonRpcDispatcherHandler(jsonOutputWriter, lineSupplier, noReceiveOnStart);
    }
}
