package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.App;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.DbusReceiveMessageHandler;
import org.asamk.signal.JsonDbusReceiveMessageHandler;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputType;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.dbus.DbusSignalControlImpl;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DaemonCommand implements MultiLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(DaemonCommand.class);
    public static DBusConnection.DBusBusType dBusType;
    public static TrustNewIdentity trustNewIdentity;
    public static OutputWriter outputWriter;

    @Override
    public String getName() {
        return "daemon";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Run in daemon mode and provide an experimental dbus interface.");
        subparser.addArgument("--system")
                .action(Arguments.storeTrue())
                .help("Use DBus system bus instead of user bus.");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--number", "--numbers").help("Phone numbers").nargs("*")
                .help("List of zero or more numbers for anonymous daemon to listen to (default=all)");
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        handleCommand(ns, m, null, outputWriter, null);
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m, final SignalCreator c, final OutputWriter outputWriter, final TrustNewIdentity trustNewIdentity) throws CommandException {
        //single-user mode
        boolean ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));

        DBusConnection.DBusBusType busType;
        if (Boolean.TRUE.equals(ns.getBoolean("system"))) {
            busType = DBusConnection.DBusBusType.SYSTEM;
        } else {
            busType = DBusConnection.DBusBusType.SESSION;
        }

        this.dBusType = busType;
        this.trustNewIdentity = trustNewIdentity;
        this.outputWriter = outputWriter;

        checkMacOS();

        try (var conn = DBusConnection.getConnection(busType)) {
            var objectPath = DbusConfig.getObjectPath();
            var t = run(conn, objectPath, m, outputWriter, ignoreAttachments);

            conn.requestBusName(DbusConfig.getBusname());

            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
        } catch (DBusException e) {
            logger.error("Dbus command failed", e);
            throw new UserErrorException("Dbus command failed, daemon already started on this bus.");
        } catch (IOException e) {
            logger.error("Dbus command failed", e);
            throw new UnexpectedErrorException("Dbus command failed", e);
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final List<Manager> managers, final SignalCreator c, final OutputWriter outputWriter, TrustNewIdentity trustNewIdentity
    ) throws CommandException {
        //anonymous mode
        boolean ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));

        DBusConnection.DBusBusType busType;
        if (Boolean.TRUE.equals(ns.getBoolean("system"))) {
            busType = DBusConnection.DBusBusType.SYSTEM;
        } else {
            busType = DBusConnection.DBusBusType.SESSION;
        }

        this.dBusType = busType;
        this.trustNewIdentity = trustNewIdentity;
        this.outputWriter = outputWriter;

        checkMacOS();

        try (var conn = DBusConnection.getConnection(busType)) {
            final var signalControl = new DbusSignalControlImpl(c, m -> {
                try {
                    final var objectPath = DbusConfig.getObjectPath(m.getSelfNumber());
                    return run(conn, objectPath, m, outputWriter, ignoreAttachments);
                } catch (DBusException e) {
                    logger.error("Failed to export object", e);
                    return null;
                }
            }, DbusConfig.getObjectPath());
            conn.exportObject(signalControl);

            List<String> daemonUsernames = ns.<String>getList("number");
            File settingsPath = c.getSettingsPath();
            ServiceEnvironment serviceEnvironment = c.getServiceEnvironment();

            if (daemonUsernames == null) {
                //--numbers option was not given, so add all local usernames
                daemonUsernames = Manager.getAllLocalNumbers(settingsPath);
                if (daemonUsernames.size() == 0) {
                    logger.error("No users are registered yet.");
                    throw new UserErrorException("No users are registered yetTry again with signal-cli daemon --numbers");
                }
            }

            for (String u : daemonUsernames) {
                try {
                    managers.add(App.loadManager(u, settingsPath, serviceEnvironment, trustNewIdentity));
                } catch (CommandException e) {
                    logger.warn("Ignoring {}: {}", u, e.getMessage());
                }
            }

            for (var m : managers) {
                signalControl.addManager(m);
            }

            conn.requestBusName(DbusConfig.getBusname());
            logger.info("Starting daemon.");

            signalControl.run();
        } catch (DBusException e) {
            logger.error("Dbus command failed", e);
            throw new UserErrorException("Dbus command failed, daemon alreadytarted on this bus.");
        } catch (IOException e ) {
            logger.error("Dbus command failed", e);
            throw new UnexpectedErrorException("Dbus command failed", e);
        }
    }

    private void checkMacOS() throws UserErrorException {
        if (System.getProperty("os.name").toLowerCase().startsWith("mac ")) {
            String dBusVar = System.getenv("DBUS_LAUNCHD_SESSION_BUS_SOCKET");
            if (dBusVar == null || dBusVar.isBlank()) {
                String message = "\n\n" +
                        "*************************************" +
                        "\n\nDBUS_LAUNCHD_SESSION_BUS_SOCKET is not set. Issue the command:\n\n" +
                        "export DBUS_LAUNCHD_SESSION_BUS_SOCKET=$(launchctl getenv DBUS_LAUNCHD_SESSION_BUS_SOCKET)\n" +
                        "\nand then try again.\n\n" +
                        "*************************************";
                throw new UserErrorException(message);
            }
        }
    }

    private Thread run(
            DBusConnection conn, String objectPath, Manager m, OutputWriter outputWriter, boolean ignoreAttachments
    ) throws DBusException {
        final var signal = new DbusSignalImpl(m, conn, objectPath);
        conn.exportObject(signal);
        final var initThread = new Thread(signal::initObjects);
        initThread.start();

        logger.info("Exported dbus object: " + objectPath);

        final var thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    final var receiveMessageHandler = outputWriter instanceof JsonWriter
                            ? new JsonDbusReceiveMessageHandler(m, (JsonWriter) outputWriter, conn, objectPath)
                            : new DbusReceiveMessageHandler(m, (PlainTextWriter) outputWriter, conn, objectPath);
                    m.receiveMessages(1, TimeUnit.HOURS, false, ignoreAttachments, receiveMessageHandler);
                    break;
                } catch (IOException e) {
                    logger.warn("Receiving messages failed, retrying", e);
                }
            }
            try {
                initThread.join();
            } catch (InterruptedException ignored) {
            }
            signal.close();
        });

        thread.start();

        return thread;
    }
}
