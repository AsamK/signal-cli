package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.DbusConfig;
import org.asamk.signal.DbusReceiveMessageHandler;
import org.asamk.signal.JsonDbusReceiveMessageHandler;
import org.asamk.signal.OutputType;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DaemonCommand implements MultiLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(DaemonCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--system")
                .action(Arguments.storeTrue())
                .help("Use DBus system bus instead of user bus.");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--json")
                .help("WARNING: This parameter is now deprecated! Please use the global \"--output=json\" option instead.\n\nOutput received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        boolean inJson = ns.get("output") == OutputType.JSON || ns.getBoolean("json");

        // TODO delete later when "json" variable is removed
        if (ns.getBoolean("json")) {
            logger.warn("\"--json\" option has been deprecated, please use the global \"--output=json\" instead.");
        }

        boolean ignoreAttachments = ns.getBoolean("ignore_attachments");

        DBusConnection.DBusBusType busType;
        if (ns.getBoolean("system")) {
            busType = DBusConnection.DBusBusType.SYSTEM;
        } else {
            busType = DBusConnection.DBusBusType.SESSION;
        }

        try (DBusConnection conn = DBusConnection.getConnection(busType)) {
            String objectPath = DbusConfig.getObjectPath();
            Thread t = run(conn, objectPath, m, ignoreAttachments, inJson);

            conn.requestBusName(DbusConfig.getBusname());

            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
            return 0;
        } catch (DBusException | IOException e) {
            logger.error("Dbus command failed", e);
            return 2;
        }
    }

    @Override
    public int handleCommand(final Namespace ns, final List<Manager> managers) {
        boolean inJson = ns.get("output") == OutputType.JSON || ns.getBoolean("json");

        // TODO delete later when "json" variable is removed
        if (ns.getBoolean("json")) {
            logger.warn("\"--json\" option has been deprecated, please use the global \"--output=json\" instead.");
        }

        boolean ignoreAttachments = ns.getBoolean("ignore_attachments");

        DBusConnection.DBusBusType busType;
        if (ns.getBoolean("system")) {
            busType = DBusConnection.DBusBusType.SYSTEM;
        } else {
            busType = DBusConnection.DBusBusType.SESSION;
        }

        try (DBusConnection conn = DBusConnection.getConnection(busType)) {
            List<Thread> receiveThreads = new ArrayList<>();
            for (Manager m : managers) {
                String objectPath = DbusConfig.getObjectPath(m.getUsername());
                Thread thread = run(conn, objectPath, m, ignoreAttachments, inJson);
                receiveThreads.add(thread);
            }

            conn.requestBusName(DbusConfig.getBusname());

            for (Thread t : receiveThreads) {
                try {
                    t.join();
                } catch (InterruptedException ignored) {
                }
            }
            return 0;
        } catch (DBusException | IOException e) {
            logger.error("Dbus command failed", e);
            return 2;
        }
    }

    private Thread run(
            DBusConnection conn, String objectPath, Manager m, boolean ignoreAttachments, boolean inJson
    ) throws DBusException {
        conn.exportObject(objectPath, new DbusSignalImpl(m));

        final Thread thread = new Thread(() -> {
            while (true) {
                try {
                    m.receiveMessages(1,
                            TimeUnit.HOURS,
                            false,
                            ignoreAttachments,
                            inJson
                                    ? new JsonDbusReceiveMessageHandler(m, conn, objectPath)
                                    : new DbusReceiveMessageHandler(m, conn, objectPath));
                } catch (IOException e) {
                    logger.warn("Receiving messages failed, retrying", e);
                }
            }
        });

        logger.info("Exported dbus object: " + objectPath);

        thread.start();

        return thread;
    }
}
