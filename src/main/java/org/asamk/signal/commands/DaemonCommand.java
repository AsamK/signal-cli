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
import org.asamk.signal.dbus.DbusSignalControlImpl;
import org.asamk.signal.dbus.DbusSignalImpl;
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
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        final var ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));

        m.setIgnoreAttachments(ignoreAttachments);
        addDefaultReceiveHandler(m, noReceiveStdOut ? null : outputWriter, receiveMode != ReceiveMode.ON_START);

        final Channel inheritedChannel;
        try {
            inheritedChannel = System.inheritedChannel();
            if (inheritedChannel instanceof ServerSocketChannel serverChannel) {
                logger.info("Using inherited socket: " + serverChannel.getLocalAddress());
                runSocketSingleAccount(m, serverChannel, receiveMode == ReceiveMode.MANUAL);
            }
        } catch (IOException e) {
            throw new IOErrorException("Failed to use inherited socket", e);
        }
        final var socketFile = ns.<File>get("socket");
        if (socketFile != null) {
            final var address = UnixDomainSocketAddress.of(socketFile.toPath());
            final var serverChannel = IOUtils.bindSocket(address);
            runSocketSingleAccount(m, serverChannel, receiveMode == ReceiveMode.MANUAL);
        }
        final var tcpAddress = ns.getString("tcp");
        if (tcpAddress != null) {
            final var address = IOUtils.parseInetSocketAddress(tcpAddress);
            final var serverChannel = IOUtils.bindSocket(address);
            runSocketSingleAccount(m, serverChannel, receiveMode == ReceiveMode.MANUAL);
        }
        final var isDbusSystem = Boolean.TRUE.equals(ns.getBoolean("dbus-system"));
        if (isDbusSystem) {
            runDbusSingleAccount(m, true, receiveMode != ReceiveMode.ON_START);
        }
        final var isDbusSession = Boolean.TRUE.equals(ns.getBoolean("dbus"));
        if (isDbusSession || (
                !isDbusSystem
                        && socketFile == null
                        && tcpAddress == null
                        && !(inheritedChannel instanceof ServerSocketChannel)
        )) {
            runDbusSingleAccount(m, false, receiveMode != ReceiveMode.ON_START);
        }

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

    @Override
    public void handleCommand(
            final Namespace ns, final MultiAccountManager c, final OutputWriter outputWriter
    ) throws CommandException {
        logger.info("Starting daemon in multi-account mode");
        final var noReceiveStdOut = Boolean.TRUE.equals(ns.getBoolean("no-receive-stdout"));
        final var receiveMode = ns.<ReceiveMode>get("receive-mode");
        final var ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));

        c.getAccountNumbers().stream().map(c::getManager).filter(Objects::nonNull).forEach(m -> {
            m.setIgnoreAttachments(ignoreAttachments);
            addDefaultReceiveHandler(m, noReceiveStdOut ? null : outputWriter, receiveMode != ReceiveMode.ON_START);
        });
        c.addOnManagerAddedHandler(m -> {
            m.setIgnoreAttachments(ignoreAttachments);
            addDefaultReceiveHandler(m, noReceiveStdOut ? null : outputWriter, receiveMode != ReceiveMode.ON_START);
        });

        final Channel inheritedChannel;
        try {
            inheritedChannel = System.inheritedChannel();
            if (inheritedChannel instanceof ServerSocketChannel serverChannel) {
                logger.info("Using inherited socket: " + serverChannel.getLocalAddress());
                runSocketMultiAccount(c, serverChannel, receiveMode == ReceiveMode.MANUAL);
            }
        } catch (IOException e) {
            throw new IOErrorException("Failed to use inherited socket", e);
        }
        final var socketFile = ns.<File>get("socket");
        if (socketFile != null) {
            final var address = UnixDomainSocketAddress.of(socketFile.toPath());
            final var serverChannel = IOUtils.bindSocket(address);
            runSocketMultiAccount(c, serverChannel, receiveMode == ReceiveMode.MANUAL);
        }
        final var tcpAddress = ns.getString("tcp");
        if (tcpAddress != null) {
            final var address = IOUtils.parseInetSocketAddress(tcpAddress);
            final var serverChannel = IOUtils.bindSocket(address);
            runSocketMultiAccount(c, serverChannel, receiveMode == ReceiveMode.MANUAL);
        }
        final var isDbusSystem = Boolean.TRUE.equals(ns.getBoolean("dbus-system"));
        if (isDbusSystem) {
            runDbusMultiAccount(c, receiveMode != ReceiveMode.ON_START, true);
        }
        final var isDbusSession = Boolean.TRUE.equals(ns.getBoolean("dbus"));
        if (isDbusSession || (
                !isDbusSystem
                        && socketFile == null
                        && tcpAddress == null
                        && !(inheritedChannel instanceof ServerSocketChannel)
        )) {
            runDbusMultiAccount(c, receiveMode != ReceiveMode.ON_START, false);
        }

        synchronized (this) {
            try {
                wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void addDefaultReceiveHandler(Manager m, OutputWriter outputWriter, final boolean isWeakListener) {
        final var handler = outputWriter instanceof JsonWriter o
                ? new JsonReceiveMessageHandler(m, o)
                : outputWriter instanceof PlainTextWriter o
                        ? new ReceiveMessageHandler(m, o)
                        : Manager.ReceiveMessageHandler.EMPTY;
        m.addReceiveHandler(handler, isWeakListener);
    }

    private void runSocketSingleAccount(
            final Manager m, final ServerSocketChannel serverChannel, final boolean noReceiveOnStart
    ) {
        runSocket(serverChannel, channel -> {
            final var handler = getSignalJsonRpcDispatcherHandler(channel, noReceiveOnStart);
            handler.handleConnection(m);
        });
    }

    private void runSocketMultiAccount(
            final MultiAccountManager c, final ServerSocketChannel serverChannel, final boolean noReceiveOnStart
    ) {
        runSocket(serverChannel, channel -> {
            final var handler = getSignalJsonRpcDispatcherHandler(channel, noReceiveOnStart);
            handler.handleConnection(c);
        });
    }

    private void runSocket(final ServerSocketChannel serverChannel, Consumer<SocketChannel> socketHandler) {
        new Thread(() -> {
            while (true) {
                final SocketChannel channel;
                final String clientString;
                try {
                    channel = serverChannel.accept();
                    clientString = channel.getRemoteAddress() + " " + IOUtils.getUnixDomainPrincipal(channel);
                    logger.info("Accepted new client: " + clientString);
                } catch (IOException e) {
                    logger.error("Failed to accept new socket connection", e);
                    synchronized (this) {
                        notifyAll();
                    }
                    break;
                }
                new Thread(() -> {
                    try (final var c = channel) {
                        socketHandler.accept(c);
                        logger.info("Connection closed: " + clientString);
                    } catch (IOException e) {
                        logger.warn("Failed to close channel", e);
                    }
                }).start();
            }
        }).start();
    }

    private SignalJsonRpcDispatcherHandler getSignalJsonRpcDispatcherHandler(
            final SocketChannel c, final boolean noReceiveOnStart
    ) {
        final var lineSupplier = IOUtils.getLineSupplier(Channels.newReader(c, StandardCharsets.UTF_8));
        final var jsonOutputWriter = new JsonWriterImpl(Channels.newWriter(c, StandardCharsets.UTF_8));

        return new SignalJsonRpcDispatcherHandler(jsonOutputWriter, lineSupplier, noReceiveOnStart);
    }

    private void runDbusSingleAccount(
            final Manager m, final boolean isDbusSystem, final boolean noReceiveOnStart
    ) throws UnexpectedErrorException {
        runDbus(isDbusSystem, (conn, objectPath) -> {
            try {
                exportDbusObject(conn, objectPath, m, noReceiveOnStart).join();
            } catch (InterruptedException ignored) {
            }
        });
    }

    private void runDbusMultiAccount(
            final MultiAccountManager c, final boolean noReceiveOnStart, final boolean isDbusSystem
    ) throws UnexpectedErrorException {
        runDbus(isDbusSystem, (connection, objectPath) -> {
            final var signalControl = new DbusSignalControlImpl(c, objectPath);
            connection.exportObject(signalControl);

            c.addOnManagerAddedHandler(m -> {
                final var thread = exportMultiAccountManager(connection, m, noReceiveOnStart);
                if (thread != null) {
                    try {
                        thread.join();
                    } catch (InterruptedException ignored) {
                    }
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
                connection.unExportObject(path);
            });

            final var initThreads = c.getAccountNumbers()
                    .stream()
                    .map(c::getManager)
                    .filter(Objects::nonNull)
                    .map(m -> exportMultiAccountManager(connection, m, noReceiveOnStart))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            for (var t : initThreads) {
                try {
                    t.join();
                } catch (InterruptedException ignored) {
                }
            }
        });
    }

    private void runDbus(
            final boolean isDbusSystem, DbusRunner dbusRunner
    ) throws UnexpectedErrorException {
        DBusConnection.DBusBusType busType;
        if (isDbusSystem) {
            busType = DBusConnection.DBusBusType.SYSTEM;
        } else {
            busType = DBusConnection.DBusBusType.SESSION;
        }
        try {
            var conn = DBusConnection.getConnection(busType);
            dbusRunner.run(conn, DbusConfig.getObjectPath());

            conn.requestBusName(DbusConfig.getBusname());

            logger.info("DBus daemon running on {} bus: {}", busType, DbusConfig.getBusname());
        } catch (DBusException e) {
            logger.error("Dbus command failed", e);
            throw new UnexpectedErrorException("Dbus command failed", e);
        }
    }

    private Thread exportMultiAccountManager(
            final DBusConnection conn, final Manager m, final boolean noReceiveOnStart
    ) {
        try {
            final var objectPath = DbusConfig.getObjectPath(m.getSelfNumber());
            return exportDbusObject(conn, objectPath, m, noReceiveOnStart);
        } catch (DBusException e) {
            logger.error("Failed to export object", e);
            return null;
        }
    }

    private Thread exportDbusObject(
            final DBusConnection conn, final String objectPath, final Manager m, final boolean noReceiveOnStart
    ) throws DBusException {
        final var signal = new DbusSignalImpl(m, conn, objectPath, noReceiveOnStart);
        conn.exportObject(signal);
        final var initThread = new Thread(signal::initObjects);
        initThread.start();

        logger.debug("Exported dbus object: " + objectPath);

        return initThread;
    }

    interface DbusRunner {

        void run(DBusConnection connection, String objectPath) throws DBusException;
    }
}
