package org.asamk.signal.dbus;

import org.asamk.signal.DbusConfig;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DbusHandler implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DbusHandler.class);

    private final boolean isDbusSystem;
    private DBusConnection dBusConnection;

    private final List<AutoCloseable> closeables = new ArrayList<>();
    private final DbusRunner dbusRunner;
    private final boolean noReceiveOnStart;

    public DbusHandler(final boolean isDbusSystem, final Manager m, final boolean noReceiveOnStart) {
        this.isDbusSystem = isDbusSystem;
        this.dbusRunner = (connection) -> {
            try {
                exportDbusObject(connection, DbusConfig.getObjectPath(), m).join();
            } catch (InterruptedException ignored) {
            }
        };
        this.noReceiveOnStart = noReceiveOnStart;
    }

    public DbusHandler(final boolean isDbusSystem, final MultiAccountManager c, final boolean noReceiveOnStart) {
        this.isDbusSystem = isDbusSystem;
        this.dbusRunner = (connection) -> {
            final var signalControl = new DbusSignalControlImpl(c, DbusConfig.getObjectPath());
            connection.exportObject(signalControl);

            c.addOnManagerAddedHandler(m -> {
                final var thread = exportManager(connection, m);
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
                        closeables.remove(dbusSignal);
                    }
                } catch (DBusException ignored) {
                }
            });

            final var initThreads = c.getManagers().stream().map(m -> exportManager(connection, m)).toList();

            for (var t : initThreads) {
                try {
                    t.join();
                } catch (InterruptedException ignored) {
                }
            }
        };
        this.noReceiveOnStart = noReceiveOnStart;
    }

    public void init() throws CommandException {
        if (dBusConnection != null) {
            throw new AssertionError("DbusHandler already initialized");
        }
        final var busType = isDbusSystem ? DBusConnection.DBusBusType.SYSTEM : DBusConnection.DBusBusType.SESSION;
        logger.debug("Starting DBus server on {} bus: {}", busType, DbusConfig.getBusname());
        try {
            dBusConnection = DBusConnectionBuilder.forType(busType).build();
            dbusRunner.run(dBusConnection);
        } catch (DBusException e) {
            throw new UnexpectedErrorException("Dbus command failed: " + e.getMessage(), e);
        } catch (UnsupportedOperationException e) {
            throw new UserErrorException("Failed to connect to Dbus: " + e.getMessage(), e);
        }

        try {
            dBusConnection.requestBusName(DbusConfig.getBusname());
        } catch (DBusException e) {
            throw new UnexpectedErrorException("Dbus command failed, maybe signal-cli dbus daemon is already running: "
                    + e.getMessage(), e);
        }

        logger.info("Started DBus server on {} bus: {}", busType, DbusConfig.getBusname());
    }

    @Override
    public void close() throws Exception {
        if (dBusConnection == null) {
            return;
        }
        dBusConnection.close();
        for (final var c : new ArrayList<>(closeables)) {
            c.close();
        }
        closeables.clear();
        dBusConnection = null;
    }

    private Thread exportDbusObject(final DBusConnection conn, final String objectPath, final Manager m) {
        final var signal = new DbusSignalImpl(m, conn, objectPath, noReceiveOnStart);
        closeables.add(signal);

        return Thread.ofPlatform().name("dbus-init-" + m.getSelfNumber()).start(signal::initObjects);
    }

    private Thread exportManager(final DBusConnection conn, final Manager m) {
        final var objectPath = DbusConfig.getObjectPath(m.getSelfNumber());
        return exportDbusObject(conn, objectPath, m);
    }

    private interface DbusRunner {

        void run(DBusConnection connection) throws DBusException;
    }
}
