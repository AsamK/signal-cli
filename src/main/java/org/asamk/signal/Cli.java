package org.asamk.signal;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.DbusCommand;
import org.asamk.signal.commands.ExtendedDbusCommand;
import org.asamk.signal.commands.LocalCommand;
import org.asamk.signal.commands.ProvisioningCommand;
import org.asamk.signal.commands.RegistrationCommand;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotRegisteredException;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.ServiceConfig;
import org.asamk.signal.util.IOUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import java.io.File;
import java.io.IOException;

public class Cli {

    private final static Logger logger = LoggerFactory.getLogger(Main.class);

    private final Namespace ns;

    public Cli(final Namespace ns) {
        this.ns = ns;
    }

    public int init() {
        Command command = getCommand();
        if (command == null) {
            logger.error("Command not implemented!");
            return 2;
        }

        if (ns.getBoolean("dbus") || ns.getBoolean("dbus_system")) {
            return initDbusClient(command, ns.getBoolean("dbus_system"));
        }

        final String username = ns.getString("username");

        final File dataPath;
        String config = ns.getString("config");
        if (config != null) {
            dataPath = new File(config);
        } else {
            dataPath = getDefaultDataPath();
        }

        final SignalServiceConfiguration serviceConfiguration = ServiceConfig.createDefaultServiceConfiguration(
                BaseConfig.USER_AGENT);

        if (!ServiceConfig.getCapabilities().isGv2()) {
            logger.warn("WARNING: Support for new group V2 is disabled,"
                    + " because the required native library dependency is missing: libzkgroup");
        }

        if (username == null) {
            ProvisioningManager pm = new ProvisioningManager(dataPath, serviceConfiguration, BaseConfig.USER_AGENT);
            return handleCommand(command, pm);
        }

        if (command instanceof RegistrationCommand) {
            final RegistrationManager manager;
            try {
                manager = RegistrationManager.init(username, dataPath, serviceConfiguration, BaseConfig.USER_AGENT);
            } catch (Throwable e) {
                logger.error("Error loading or creating state file: {}", e.getMessage());
                return 1;
            }
            try (RegistrationManager m = manager) {
                return handleCommand(command, m);
            } catch (Exception e) {
                logger.error("Cleanup failed", e);
                return 2;
            }
        }

        Manager manager;
        try {
            manager = Manager.init(username, dataPath, serviceConfiguration, BaseConfig.USER_AGENT);
        } catch (NotRegisteredException e) {
            System.err.println("User is not registered.");
            return 0;
        } catch (Throwable e) {
            logger.error("Error loading state file: {}", e.getMessage());
            return 1;
        }

        try (Manager m = manager) {
            try {
                m.checkAccountState();
            } catch (IOException e) {
                logger.error("Error while checking account: {}", e.getMessage());
                return 1;
            }

            return handleCommand(command, m);
        } catch (IOException e) {
            logger.error("Cleanup failed", e);
            return 2;
        }
    }

    private Command getCommand() {
        String commandKey = ns.getString("command");
        return Commands.getCommand(commandKey);
    }

    private int initDbusClient(final Command command, final boolean systemBus) {
        try {
            DBusConnection.DBusBusType busType;
            if (systemBus) {
                busType = DBusConnection.DBusBusType.SYSTEM;
            } else {
                busType = DBusConnection.DBusBusType.SESSION;
            }
            try (DBusConnection dBusConn = DBusConnection.getConnection(busType)) {
                Signal ts = dBusConn.getRemoteObject(DbusConfig.SIGNAL_BUSNAME,
                        DbusConfig.SIGNAL_OBJECTPATH,
                        Signal.class);

                return handleCommand(command, ts, dBusConn);
            }
        } catch (DBusException | IOException e) {
            logger.error("Dbus client failed", e);
            return 2;
        }
    }

    private int handleCommand(Command command, Signal ts, DBusConnection dBusConn) {
        if (command instanceof ExtendedDbusCommand) {
            return ((ExtendedDbusCommand) command).handleCommand(ns, ts, dBusConn);
        } else if (command instanceof DbusCommand) {
            return ((DbusCommand) command).handleCommand(ns, ts);
        } else {
            System.err.println("Command is not yet implemented via dbus");
            return 1;
        }
    }

    private int handleCommand(Command command, ProvisioningManager pm) {
        if (command instanceof ProvisioningCommand) {
            return ((ProvisioningCommand) command).handleCommand(ns, pm);
        } else {
            System.err.println("Command only works with a username");
            return 1;
        }
    }

    private int handleCommand(Command command, RegistrationManager m) {
        if (command instanceof RegistrationCommand) {
            return ((RegistrationCommand) command).handleCommand(ns, m);
        }
        return 1;
    }

    private int handleCommand(Command command, Manager m) {
        if (command instanceof LocalCommand) {
            return ((LocalCommand) command).handleCommand(ns, m);
        } else if (command instanceof DbusCommand) {
            return ((DbusCommand) command).handleCommand(ns, new DbusSignalImpl(m));
        } else {
            System.err.println("Command only works via dbus");
            return 1;
        }
    }

    /**
     * Uses $XDG_DATA_HOME/signal-cli if it exists, or if none of the legacy directories exist:
     * - $HOME/.config/signal
     * - $HOME/.config/textsecure
     *
     * @return the data directory to be used by signal-cli.
     */
    private static File getDefaultDataPath() {
        File dataPath = new File(IOUtils.getDataHomeDir(), "signal-cli");
        if (dataPath.exists()) {
            return dataPath;
        }

        File configPath = new File(System.getProperty("user.home"), ".config");

        File legacySettingsPath = new File(configPath, "signal");
        if (legacySettingsPath.exists()) {
            return legacySettingsPath;
        }

        legacySettingsPath = new File(configPath, "textsecure");
        if (legacySettingsPath.exists()) {
            return legacySettingsPath;
        }

        return dataPath;
    }
}
