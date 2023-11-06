package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.DbusConfig;
import org.asamk.signal.OutputType;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.dbus.DbusSignalControlImpl;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.http.HttpServerHandler;
import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.jsonrpc.SignalJsonRpcDispatcherHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.JsonWriterImpl;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.IOUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.asamk.signal.util.CommandUtil.getReceiveConfig;

public class DaemonCommand implements MultiLocalCommand, LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(DaemonCommand.class);

    @Override
    public String getName() {
        return "daemon";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        final var defaultSocketPath = new File(new File(IOUtils.getRuntimeDir(), "signal-cli"), "socket");
        subparser.help("Run in daemon mode and provide an experimental dbus or JSON-RPC interface.");
        subparser.addArgument("--dbus")
                .action(Arguments.storeTrue())
                .help("Expose a DBus interface on the user bus (the default, if no other options are given).");
        subparser.addArgument("--dbus-system", "--system")
                .action(Arguments.storeTrue())
                .help("Expose a DBus interface on the system bus.");
        subparser.addArgument("--socket")
                .nargs("?")
                .type(File.class)
                .setConst(defaultSocketPath)
                .help("Expose a JSON-RPC interface on a UNIX socket (default $XDG_RUNTIME_DIR/signal-cli/socket).");
        subparser.addArgument("--tcp")
                .nargs("?")
                .setConst("localhost:7583")
                .help("Expose a JSON-RPC interface on a TCP socket (default localhost:7583).");
        subparser.addArgument("--http")
                .nargs("?")
                .setConst("localhost:8080")
                .help("Expose a JSON-RPC interface as http endpoint (default localhost:8080).");
        subparser.addArgument("--no-receive-stdout")
                .help("Don’t print received messages to stdout.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--receive-mode")
                .help("Specify when to start receiving messages.")
                .type(Arguments.enumStringType(ReceiveMode.class))
                .setDefault(ReceiveMode.ON_START);
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--ignore-stories")
                .help("Don’t receive story messages from the server.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--send-read-receipts")
                .help("Send read receipts for all incoming data messages (in addition to the default delivery receipts)")
                .action(Arguments.storeTrue());
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        logger.info("Starting daemon in single-account mode for " + m.getSelfNumber());
        final var noReceiveStdOut = Boolean.TRUE.equals(ns.getBoolean("no-receive-stdout"));
        final var receiveMode = ns.<ReceiveMode>get("receive-mode");
        final var receiveConfig = getReceiveConfig(ns);

        m.setReceiveConfig(receiveConfig);
        addDefaultReceiveHandler(m, noReceiveStdOut ? null : outputWriter, receiveMode != ReceiveMode.ON_START);

        try (final var daemonHandler = new SingleAccountDaemonHandler(m, receiveMode)) {
            setup(ns, daemonHandler);

            m.addClosedListener(() -> {
                synchronized (this) {
                    notifyAll();
                }
            });

            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final MultiAccountManager c, final OutputWriter outputWriter
    ) throws CommandException {
        logger.info("Starting daemon in multi-account mode");
        final var noReceiveStdOut = Boolean.TRUE.equals(ns.getBoolean("no-receive-stdout"));
        final var receiveMode = ns.<ReceiveMode>get("receive-mode");
        final var receiveConfig = getReceiveConfig(ns);
        c.getManagers().forEach(m -> {
            m.setReceiveConfig(receiveConfig);
            addDefaultReceiveHandler(m, noReceiveStdOut ? null : outputWriter, receiveMode != ReceiveMode.ON_START);
        });
        c.addOnManagerAddedHandler(m -> {
            m.setReceiveConfig(receiveConfig);
            addDefaultReceiveHandler(m, noReceiveStdOut ? null : outputWriter, receiveMode != ReceiveMode.ON_START);
        });

        try (final var daemonHandler = new MultiAccountDaemonHandler(c, receiveMode)) {
            setup(ns, daemonHandler);

            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static void setup(final Namespace ns, final DaemonHandler daemonHandler) throws CommandException {
        final Channel inheritedChannel;
        try {
            inheritedChannel = System.inheritedChannel();
            if (inheritedChannel instanceof ServerSocketChannel serverChannel) {
                logger.info("Using inherited socket: " + serverChannel.getLocalAddress());
                daemonHandler.runSocket(serverChannel);
            }
        } catch (IOException e) {
            throw new IOErrorException("Failed to use inherited socket", e);
        }

        final var socketFile = ns.<File>get("socket");
        if (socketFile != null) {
            final var address = UnixDomainSocketAddress.of(socketFile.toPath());
            final var serverChannel = IOUtils.bindSocket(address);
            daemonHandler.runSocket(serverChannel);
        }

        final var tcpAddress = ns.getString("tcp");
        if (tcpAddress != null) {
            final var address = IOUtils.parseInetSocketAddress(tcpAddress);
            final var serverChannel = IOUtils.bindSocket(address);
            daemonHandler.runSocket(serverChannel);
        }

        final var httpAddress = ns.getString("http");
        if (httpAddress != null) {
            final var address = IOUtils.parseInetSocketAddress(httpAddress);
            daemonHandler.runHttp(address);
        }

        final var isDbusSystem = Boolean.TRUE.equals(ns.getBoolean("dbus-system"));
        if (isDbusSystem) {
            daemonHandler.runDbus(true);
        }

        final var isDbusSession = Boolean.TRUE.equals(ns.getBoolean("dbus"));
        if (isDbusSession || (
                !isDbusSystem
                        && socketFile == null
                        && tcpAddress == null
                        && httpAddress == null
                        && !(inheritedChannel instanceof ServerSocketChannel)
        )) {
            daemonHandler.runDbus(false);
        }
    }

    private void addDefaultReceiveHandler(Manager m, OutputWriter outputWriter, final boolean isWeakListener) {
        final var handler = switch (outputWriter) {
            case PlainTextWriter writer -> new ReceiveMessageHandler(m, writer);
            case JsonWriter writer -> new JsonReceiveMessageHandler(m, writer);
            case null -> Manager.ReceiveMessageHandler.EMPTY;
        };
        m.addReceiveHandler(handler, isWeakListener);
    }

    private static abstract class DaemonHandler implements AutoCloseable {

        protected final ReceiveMode receiveMode;
        private static final AtomicInteger threadNumber = new AtomicInteger(0);

        public DaemonHandler(final ReceiveMode receiveMode) {
            this.receiveMode = receiveMode;
        }

        public abstract void runSocket(ServerSocketChannel serverChannel) throws CommandException;

        public abstract void runDbus(boolean isDbusSystem) throws CommandException;

        public abstract void runHttp(InetSocketAddress address) throws CommandException;

        protected void runSocket(final ServerSocketChannel serverChannel, Consumer<SocketChannel> socketHandler) {
            Thread.ofPlatform().name("daemon-listener").start(() -> {
                try (final var executor = Executors.newCachedThreadPool()) {
                    while (true) {
                        final var connectionId = threadNumber.getAndIncrement();
                        final SocketChannel channel;
                        final String clientString;
                        try {
                            channel = serverChannel.accept();
                            clientString = channel.getRemoteAddress() + " " + IOUtils.getUnixDomainPrincipal(channel);
                            logger.info("Accepted new client connection {}: {}", connectionId, clientString);
                        } catch (IOException e) {
                            logger.error("Failed to accept new socket connection", e);
                            break;
                        }
                        executor.submit(() -> {
                            try (final var c = channel) {
                                socketHandler.accept(c);
                            } catch (IOException e) {
                                logger.warn("Failed to close channel", e);
                            } catch (Throwable e) {
                                logger.warn("Connection handler failed, closing connection", e);
                            }
                            logger.info("Connection {} closed: {}", connectionId, clientString);
                        });
                    }
                }
            });
        }

        protected SignalJsonRpcDispatcherHandler getSignalJsonRpcDispatcherHandler(final SocketChannel c) {
            final var lineSupplier = IOUtils.getLineSupplier(Channels.newReader(c, StandardCharsets.UTF_8));
            final var jsonOutputWriter = new JsonWriterImpl(Channels.newWriter(c, StandardCharsets.UTF_8));

            return new SignalJsonRpcDispatcherHandler(jsonOutputWriter,
                    lineSupplier,
                    receiveMode == ReceiveMode.MANUAL);
        }

        protected Thread exportDbusObject(final DBusConnection conn, final String objectPath, final Manager m) {
            final var signal = new DbusSignalImpl(m, conn, objectPath, receiveMode != ReceiveMode.ON_START);

            return Thread.ofPlatform().name("dbus-init-" + m.getSelfNumber()).start(signal::initObjects);
        }

        protected void runDbus(
                final boolean isDbusSystem, MultiAccountDaemonHandler.DbusRunner dbusRunner
        ) throws CommandException {
            DBusConnection.DBusBusType busType;
            if (isDbusSystem) {
                busType = DBusConnection.DBusBusType.SYSTEM;
            } else {
                busType = DBusConnection.DBusBusType.SESSION;
            }
            DBusConnection conn;
            try {
                conn = DBusConnectionBuilder.forType(busType).build();
                dbusRunner.run(conn, DbusConfig.getObjectPath());
            } catch (DBusException e) {
                throw new UnexpectedErrorException("Dbus command failed: " + e.getMessage(), e);
            } catch (UnsupportedOperationException e) {
                throw new UserErrorException("Failed to connect to Dbus: " + e.getMessage(), e);
            }

            try {
                conn.requestBusName(DbusConfig.getBusname());
            } catch (DBusException e) {
                throw new UnexpectedErrorException(
                        "Dbus command failed, maybe signal-cli dbus daemon is already running: " + e.getMessage(),
                        e);
            }

            logger.info("DBus daemon running on {} bus: {}", busType, DbusConfig.getBusname());
        }

        @Override
        public void close() {
            // TODO
        }
    }

    private static final class SingleAccountDaemonHandler extends DaemonHandler {

        private final Manager m;

        private SingleAccountDaemonHandler(final Manager m, final ReceiveMode receiveMode) {
            super(receiveMode);
            this.m = m;
        }

        @Override
        public void runSocket(final ServerSocketChannel serverChannel) {
            runSocket(serverChannel, channel -> {
                final var handler = getSignalJsonRpcDispatcherHandler(channel);
                handler.handleConnection(m);
            });
        }

        @Override
        public void runDbus(final boolean isDbusSystem) throws CommandException {
            runDbus(isDbusSystem, (conn, objectPath) -> {
                try {
                    exportDbusObject(conn, objectPath, m).join();
                } catch (InterruptedException ignored) {
                }
            });
        }

        @Override
        public void runHttp(InetSocketAddress address) throws CommandException {
            final var handler = new HttpServerHandler(address, m);
            try {
                handler.init();
            } catch (IOException ex) {
                throw new IOErrorException("Failed to initialize HTTP Server", ex);
            }
        }
    }

    private static final class MultiAccountDaemonHandler extends DaemonHandler {

        private final MultiAccountManager c;

        private MultiAccountDaemonHandler(final MultiAccountManager c, final ReceiveMode receiveMode) {
            super(receiveMode);
            this.c = c;
        }

        public void runSocket(final ServerSocketChannel serverChannel) {
            runSocket(serverChannel, channel -> {
                final var handler = getSignalJsonRpcDispatcherHandler(channel);
                handler.handleConnection(c);
            });
        }

        public void runDbus(final boolean isDbusSystem) throws CommandException {
            runDbus(isDbusSystem, (connection, objectPath) -> {
                final var signalControl = new DbusSignalControlImpl(c, objectPath);
                connection.exportObject(signalControl);

                c.addOnManagerAddedHandler(m -> {
                    final var thread = exportMultiAccountManager(connection, m);
                    try {
                        thread.join();
                    } catch (InterruptedException ignored) {
                    }
                });
                c.addOnManagerRemovedHandler(m -> {
                    final var path = DbusConfig.getObjectPath(m.getSelfNumber());
                    try {
                        final var object = connection.getExportedObject(null, path);
                        if (object instanceof DbusSignalImpl dbusSignal) {
                            dbusSignal.close();
                        }
                    } catch (DBusException ignored) {
                    }
                });

                final var initThreads = c.getManagers()
                        .stream()
                        .map(m -> exportMultiAccountManager(connection, m))
                        .toList();

                for (var t : initThreads) {
                    try {
                        t.join();
                    } catch (InterruptedException ignored) {
                    }
                }
            });
        }

        @Override
        public void runHttp(final InetSocketAddress address) throws CommandException {
            final var handler = new HttpServerHandler(address, c);
            try {
                handler.init();
            } catch (IOException ex) {
                throw new IOErrorException("Failed to initialize HTTP Server", ex);
            }
        }

        private Thread exportMultiAccountManager(
                final DBusConnection conn, final Manager m
        ) {
            final var objectPath = DbusConfig.getObjectPath(m.getSelfNumber());
            return exportDbusObject(conn, objectPath, m);
        }

        interface DbusRunner {

            void run(DBusConnection connection, String objectPath) throws DBusException;
        }
    }
}
