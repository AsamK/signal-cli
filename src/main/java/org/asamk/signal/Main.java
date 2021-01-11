/*
  Copyright (C) 2015-2021 AsamK and contributors

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.asamk.signal;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.MutuallyExclusiveGroup;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

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
import org.asamk.signal.util.SecurityProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.util.Map;

public class Main {

    final static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        installSecurityProviderWorkaround();

        Namespace ns = parseArgs(args);
        if (ns == null) {
            System.exit(1);
        }

        int res = init(ns);
        System.exit(res);
    }

    public static void installSecurityProviderWorkaround() {
        // Register our own security provider
        Security.insertProviderAt(new SecurityProvider(), 1);
        Security.addProvider(new BouncyCastleProvider());
    }

    public static int init(Namespace ns) {
        Command command = getCommand(ns);
        if (command == null) {
            logger.error("Command not implemented!");
            return 3;
        }

        if (ns.getBoolean("dbus") || ns.getBoolean("dbus_system")) {
            return initDbusClient(command, ns, ns.getBoolean("dbus_system"));
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
            return handleCommand(command, ns, pm);
        }

        if (command instanceof RegistrationCommand) {
            final RegistrationManager manager;
            try {
                manager = RegistrationManager.init(username, dataPath, serviceConfiguration, BaseConfig.USER_AGENT);
            } catch (Throwable e) {
                logger.error("Error loading or creating state file: {}", e.getMessage());
                return 2;
            }
            try (RegistrationManager m = manager) {
                return handleCommand(command, ns, m);
            } catch (Exception e) {
                logger.error("Cleanup failed", e);
                return 3;
            }
        }

        Manager manager;
        try {
            manager = Manager.init(username, dataPath, serviceConfiguration, BaseConfig.USER_AGENT);
        } catch (NotRegisteredException e) {
            System.err.println("User is not registered.");
            return 1;
        } catch (Throwable e) {
            logger.error("Error loading state file: {}", e.getMessage());
            return 2;
        }

        try (Manager m = manager) {
            try {
                m.checkAccountState();
            } catch (IOException e) {
                logger.error("Error while checking account: {}", e.getMessage());
                return 2;
            }

            return handleCommand(command, ns, m);
        } catch (IOException e) {
            logger.error("Cleanup failed", e);
            return 3;
        }
    }

    private static int initDbusClient(final Command command, final Namespace ns, final boolean systemBus) {
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

                return handleCommand(command, ns, ts, dBusConn);
            }
        } catch (DBusException | IOException e) {
            logger.error("Dbus client failed", e);
            return 3;
        }
    }

    private static Command getCommand(Namespace ns) {
        String commandKey = ns.getString("command");
        final Map<String, Command> commands = Commands.getCommands();
        if (!commands.containsKey(commandKey)) {
            return null;
        }
        return commands.get(commandKey);
    }

    private static int handleCommand(Command command, Namespace ns, Signal ts, DBusConnection dBusConn) {
        if (command instanceof ExtendedDbusCommand) {
            return ((ExtendedDbusCommand) command).handleCommand(ns, ts, dBusConn);
        } else if (command instanceof DbusCommand) {
            return ((DbusCommand) command).handleCommand(ns, ts);
        } else {
            System.err.println("Command is not yet implemented via dbus");
            return 1;
        }
    }

    private static int handleCommand(Command command, Namespace ns, ProvisioningManager pm) {
        if (command instanceof ProvisioningCommand) {
            return ((ProvisioningCommand) command).handleCommand(ns, pm);
        } else {
            System.err.println("Command only works with a username");
            return 1;
        }
    }

    private static int handleCommand(Command command, Namespace ns, RegistrationManager m) {
        if (command instanceof RegistrationCommand) {
            return ((RegistrationCommand) command).handleCommand(ns, m);
        }
        return 1;
    }

    private static int handleCommand(Command command, Namespace ns, Manager m) {
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

    private static Namespace parseArgs(String[] args) {
        ArgumentParser parser = buildArgumentParser();

        Namespace ns;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            return null;
        }

        if ("link".equals(ns.getString("command"))) {
            if (ns.getString("username") != null) {
                parser.printUsage();
                System.err.println("You cannot specify a username (phone number) when linking");
                System.exit(2);
            }
        } else if (!ns.getBoolean("dbus") && !ns.getBoolean("dbus_system")) {
            if (ns.getString("username") == null) {
                parser.printUsage();
                System.err.println("You need to specify a username (phone number)");
                System.exit(2);
            }
            if (!PhoneNumberFormatter.isValidNumber(ns.getString("username"), null)) {
                System.err.println("Invalid username (phone number), make sure you include the country code.");
                System.exit(2);
            }
        }
        if (ns.getList("recipient") != null && !ns.getList("recipient").isEmpty() && ns.getString("group") != null) {
            System.err.println("You cannot specify recipients by phone number and groups at the same time");
            System.exit(2);
        }
        return ns;
    }

    private static ArgumentParser buildArgumentParser() {
        ArgumentParser parser = ArgumentParsers.newFor("signal-cli")
                .build()
                .defaultHelp(true)
                .description("Commandline interface for Signal.")
                .version(BaseConfig.PROJECT_NAME + " " + BaseConfig.PROJECT_VERSION);

        parser.addArgument("-v", "--version").help("Show package version.").action(Arguments.version());
        parser.addArgument("--config")
                .help("Set the path, where to store the config (Default: $XDG_DATA_HOME/signal-cli , $HOME/.local/share/signal-cli).");

        MutuallyExclusiveGroup mut = parser.addMutuallyExclusiveGroup();
        mut.addArgument("-u", "--username").help("Specify your phone number, that will be used for verification.");
        mut.addArgument("--dbus").help("Make request via user dbus.").action(Arguments.storeTrue());
        mut.addArgument("--dbus-system").help("Make request via system dbus.").action(Arguments.storeTrue());

        Subparsers subparsers = parser.addSubparsers()
                .title("subcommands")
                .dest("command")
                .description("valid subcommands")
                .help("additional help");

        final Map<String, Command> commands = Commands.getCommands();
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            Subparser subparser = subparsers.addParser(entry.getKey());
            entry.getValue().attachToSubparser(subparser);
        }
        return parser;
    }
}
