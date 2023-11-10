package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.Shutdown;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.dbus.DbusHandler;
import org.asamk.signal.http.HttpServerHandler;
import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.jsonrpc.SocketHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;

import static org.asamk.signal.util.CommandUtil.getReceiveConfig;

public class DaemonCommand implements MultiLocalCommand, LocalCommand {

    private static final Logger logger = LoggerFactory.getLogger(DaemonCommand.class);

    @Override
    public String getName() {
        return "daemon";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        final var defaultSocketPath = new File(new File(IOUtils.getRuntimeDir(), "signal-cli"), "socket");
        subparser.help("Run in daemon mode and provide a JSON-RPC or an experimental dbus interface.");
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
        Shutdown.installHandler();
        logger.info("Starting daemon in single-account mode for " + m.getSelfNumber());
        final var noReceiveStdOut = Boolean.TRUE.equals(ns.getBoolean("no-receive-stdout"));
        final var receiveMode = ns.<ReceiveMode>get("receive-mode");
        final var receiveConfig = getReceiveConfig(ns);

        m.setReceiveConfig(receiveConfig);
        addDefaultReceiveHandler(m, noReceiveStdOut ? null : outputWriter, receiveMode != ReceiveMode.ON_START);

        try (final var daemonHandler = new SingleAccountDaemonHandler(m, receiveMode)) {
            setup(ns, daemonHandler);

            m.addClosedListener(Shutdown::triggerShutdown);

            try {
                Shutdown.waitForShutdown();
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final MultiAccountManager c, final OutputWriter outputWriter
    ) throws CommandException {
        Shutdown.installHandler();
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
                    Shutdown.waitForShutdown();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static void setup(final Namespace ns, final DaemonHandler daemonHandler) throws CommandException {
        final Channel inheritedChannel;
        try {
            if (System.inheritedChannel() instanceof ServerSocketChannel serverChannel) {
                inheritedChannel = serverChannel;
                logger.info("Using inherited socket: " + serverChannel.getLocalAddress());
                daemonHandler.runSocket(serverChannel);
            } else {
                inheritedChannel = null;
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
                        && inheritedChannel == null
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
        protected final List<AutoCloseable> closeables = new ArrayList<>();

        protected DaemonHandler(final ReceiveMode receiveMode) {
            this.receiveMode = receiveMode;
        }

        public abstract void runSocket(ServerSocketChannel serverChannel) throws CommandException;

        public abstract void runDbus(boolean isDbusSystem) throws CommandException;

        public abstract void runHttp(InetSocketAddress address) throws CommandException;

        protected final void runSocket(final SocketHandler socketHandler) {
            socketHandler.init();
            this.closeables.add(socketHandler);
        }

        protected final void runDbus(
                DbusHandler dbusHandler
        ) throws CommandException {
            dbusHandler.init();
            this.closeables.add(dbusHandler);
        }

        protected final void runHttp(final HttpServerHandler handler) throws CommandException {
            try {
                handler.init();
            } catch (IOException ex) {
                throw new IOErrorException("Failed to initialize HTTP Server", ex);
            }
            this.closeables.add(handler);
        }

        @Override
        public void close() {
            for (final var closeable : new ArrayList<>(this.closeables)) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    logger.warn("Failed to close daemon handler", e);
                }
            }
            this.closeables.clear();
        }
    }

    private static final class SingleAccountDaemonHandler extends DaemonHandler {

        private final Manager m;

        public SingleAccountDaemonHandler(final Manager m, final ReceiveMode receiveMode) {
            super(receiveMode);
            this.m = m;
        }

        @Override
        public void runSocket(final ServerSocketChannel serverChannel) {
            runSocket(new SocketHandler(serverChannel, m, receiveMode == ReceiveMode.MANUAL));
        }

        @Override
        public void runDbus(final boolean isDbusSystem) throws CommandException {
            runDbus(new DbusHandler(isDbusSystem, m, receiveMode != ReceiveMode.ON_START));
        }

        @Override
        public void runHttp(InetSocketAddress address) throws CommandException {
            runHttp(new HttpServerHandler(address, m));
        }
    }

    private static final class MultiAccountDaemonHandler extends DaemonHandler {

        private final MultiAccountManager c;

        public MultiAccountDaemonHandler(final MultiAccountManager c, final ReceiveMode receiveMode) {
            super(receiveMode);
            this.c = c;
        }

        @Override
        public void runSocket(final ServerSocketChannel serverChannel) {
            runSocket(new SocketHandler(serverChannel, c, receiveMode == ReceiveMode.MANUAL));
        }

        @Override
        public void runDbus(final boolean isDbusSystem) throws CommandException {
            runDbus(new DbusHandler(isDbusSystem, c, receiveMode != ReceiveMode.ON_START));
        }

        @Override
        public void runHttp(final InetSocketAddress address) throws CommandException {
            runHttp(new HttpServerHandler(address, c));
        }
    }
}
