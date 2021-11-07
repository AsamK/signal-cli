package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.DbusConfig;
import org.asamk.signal.DbusReceiveMessageHandler;
import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputType;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.ReceiveMessageHandler;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.dbus.DbusSignalControlImpl;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class DaemonCommand implements MultiLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(DaemonCommand.class);

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
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        boolean ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));
        m.setIgnoreAttachments(ignoreAttachments);

        DBusConnection.DBusBusType busType;
        if (Boolean.TRUE.equals(ns.getBoolean("system"))) {
            busType = DBusConnection.DBusBusType.SYSTEM;
        } else {
            busType = DBusConnection.DBusBusType.SESSION;
        }

        try (var conn = DBusConnection.getConnection(busType)) {
            var objectPath = DbusConfig.getObjectPath();
            var t = run(conn, objectPath, m, outputWriter);

            conn.requestBusName(DbusConfig.getBusname());
            logger.info("DBus daemon running in single-user mode for " + m.getSelfNumber());

            try {
                t.join();
                synchronized (this) {
                    wait();
                }
            } catch (InterruptedException ignored) {
            }
        } catch (DBusException | IOException e) {
            logger.error("Dbus command failed", e);
            throw new UnexpectedErrorException("Dbus command failed", e);
        }
    }

    @Override
    public void handleCommand(
            final Namespace ns, final List<Manager> managers, final SignalCreator c, final OutputWriter outputWriter
    ) throws CommandException {
        boolean ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));

        DBusConnection.DBusBusType busType;
        if (Boolean.TRUE.equals(ns.getBoolean("system"))) {
            busType = DBusConnection.DBusBusType.SYSTEM;
        } else {
            busType = DBusConnection.DBusBusType.SESSION;
        }

        try (var conn = DBusConnection.getConnection(busType)) {
            final var signalControl = new DbusSignalControlImpl(c, m -> {
                m.setIgnoreAttachments(ignoreAttachments);
                try {
                    final var objectPath = DbusConfig.getObjectPath(m.getSelfNumber());
                    return run(conn, objectPath, m, outputWriter);
                } catch (DBusException e) {
                    logger.error("Failed to export object", e);
                    return null;
                }
            }, DbusConfig.getObjectPath());
            conn.exportObject(signalControl);

            for (var m : managers) {
                signalControl.addManager(m);
            }

            conn.requestBusName(DbusConfig.getBusname());
            logger.info("DBus daemon running in mulit-account mode");

            signalControl.run();
        } catch (DBusException | IOException e) {
            logger.error("Dbus command failed", e);
            throw new UnexpectedErrorException("Dbus command failed", e);
        }
    }

    private Thread run(
            DBusConnection conn, String objectPath, Manager m, OutputWriter outputWriter
    ) throws DBusException {
        final var signal = new DbusSignalImpl(m, conn, objectPath);
        conn.exportObject(signal);
        final var initThread = new Thread(signal::initObjects);
        initThread.start();

        logger.debug("Exported dbus object: " + objectPath);

        final var handler = outputWriter instanceof JsonWriter ? new JsonReceiveMessageHandler(m,
                (JsonWriter) outputWriter) : new ReceiveMessageHandler(m, (PlainTextWriter) outputWriter);
        m.addReceiveHandler(handler);

        final var dbusMessageHandler = new DbusReceiveMessageHandler(m, conn, objectPath);
        m.addReceiveHandler(dbusMessageHandler);

        return initThread;
    }
}
