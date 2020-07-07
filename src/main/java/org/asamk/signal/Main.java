/*
  Copyright (C) 2015-2020 AsamK and contributors

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
import org.asamk.signal.storage.SignalAccount;
import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.DbusCommand;
import org.asamk.signal.commands.ExtendedDbusCommand;
import org.asamk.signal.commands.LocalCommand;
import org.asamk.signal.commands.ProvisioningCommand;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.ServiceConfig;
import org.asamk.signal.manager.PathConfig;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.SecurityProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.util.Map;

import static org.whispersystems.signalservice.internal.util.Util.isEmpty;

public class Main {

    public static void main(String[] args) {
        installSecurityProviderWorkaround();

        Namespace ns = parseArgs(args);
        if (ns == null) {
            System.exit(1);
        }

        int res = handleCommands(ns);
        System.exit(res);
    }

    public static void installSecurityProviderWorkaround() {
        // Register our own security provider
        Security.insertProviderAt(new SecurityProvider(), 1);
        Security.addProvider(new BouncyCastleProvider());
    }

    private static int handleCommands(Namespace ns) {
        String username = ns.getString("username");

        if (ns.getBoolean("dbus") || ns.getBoolean("dbus_system")) {
            try {
                DBusConnection.DBusBusType busType;
                if (ns.getBoolean("dbus_system")) {
                    busType = DBusConnection.DBusBusType.SYSTEM;
                } else {
                    busType = DBusConnection.DBusBusType.SESSION;
                }
                try (DBusConnection dBusConn = DBusConnection.getConnection(busType)) {
                    Signal ts = dBusConn.getRemoteObject(
                            DbusConfig.SIGNAL_BUSNAME, DbusConfig.SIGNAL_OBJECTPATH,
                            Signal.class);

                    return handleCommands(ns, ts, dBusConn);
                }
            } catch (UnsatisfiedLinkError e) {
                System.err.println("Missing native library dependency for dbus service: " + e.getMessage());
                return 1;
            } catch (DBusException | IOException e) {
                e.printStackTrace();
                return 3;
            }
        } else {
            String dataPath = ns.getString("config");
            if (isEmpty(dataPath)) {
                dataPath = getDefaultDataPath();
            }

            if (ns.getBoolean("singleuser")) {
                String completeDataPath = PathConfig.createDefault(dataPath).getDataPath();
                username = SignalAccount.getSingleUser(completeDataPath);
                if (username == null) {
                    System.exit(1);
                }
                System.out.println("Assuming username: " + username);
                ns.getAttrs().put("username", username);
            }

            final SignalServiceConfiguration serviceConfiguration = ServiceConfig.createDefaultServiceConfiguration(BaseConfig.USER_AGENT);

            if (username == null) {
                ProvisioningManager pm = new ProvisioningManager(dataPath, serviceConfiguration, BaseConfig.USER_AGENT);
                return handleCommands(ns, pm);
            }

            Manager manager;
            try {
                manager = Manager.init(username, dataPath, serviceConfiguration, BaseConfig.USER_AGENT);
            } catch (Throwable e) {
                System.err.println("Error loading state file: " + e.getMessage());
                return 2;
            }

            try (Manager m = manager) {
                try {
                    m.checkAccountState();
                } catch (AuthorizationFailedException e) {
                    if (!"register".equals(ns.getString("command"))) {
                        // Register command should still be possible, if current authorization fails
                        System.err.println("Authorization failed, was the number registered elsewhere?");
                        return 2;
                    }
                } catch (IOException e) {
                    System.err.println("Error while checking account: " + e.getMessage());
                    return 2;
                }

                return handleCommands(ns, m);
            } catch (IOException e) {
                e.printStackTrace();
                return 3;
            }
        }
    }

    private static int handleCommands(Namespace ns, Signal ts, DBusConnection dBusConn) {
        String commandKey = ns.getString("command");
        final Map<String, Command> commands = Commands.getCommands();
        if (commands.containsKey(commandKey)) {
            Command command = commands.get(commandKey);

            if (command instanceof ExtendedDbusCommand) {
                return ((ExtendedDbusCommand) command).handleCommand(ns, ts, dBusConn);
            } else if (command instanceof DbusCommand) {
                return ((DbusCommand) command).handleCommand(ns, ts);
            } else {
                System.err.println(commandKey + " is not yet implemented via dbus");
                return 1;
            }
        }
        return 0;
    }

    private static int handleCommands(Namespace ns, ProvisioningManager pm) {
        String commandKey = ns.getString("command");
        final Map<String, Command> commands = Commands.getCommands();
        if (commands.containsKey(commandKey)) {
            Command command = commands.get(commandKey);

            if (command instanceof ProvisioningCommand) {
                return ((ProvisioningCommand) command).handleCommand(ns, pm);
            } else {
                System.err.println(commandKey + " only works with a username");
                return 1;
            }
        }
        return 0;
    }

    private static int handleCommands(Namespace ns, Manager m) {
        String commandKey = ns.getString("command");
        final Map<String, Command> commands = Commands.getCommands();
        if (commands.containsKey(commandKey)) {
            Command command = commands.get(commandKey);

            if (command instanceof LocalCommand) {
                return ((LocalCommand) command).handleCommand(ns, m);
            } else if (command instanceof DbusCommand) {
                return ((DbusCommand) command).handleCommand(ns, new DbusSignalImpl(m));
            } else if (command instanceof ExtendedDbusCommand) {
                System.err.println(commandKey + " only works via dbus");
            }
            return 1;
        }
        return 0;
    }

    /**
     * Uses $XDG_DATA_HOME/signal-cli if it exists, or if none of the legacy directories exist:
     * - $HOME/.config/signal
     * - $HOME/.config/textsecure
     *
     * @return the data directory to be used by signal-cli.
     */
    private static String getDefaultDataPath() {
        String dataPath = IOUtils.getDataHomeDir() + "/signal-cli";
        if (new File(dataPath).exists()) {
            return dataPath;
        }

        String legacySettingsPath = System.getProperty("user.home") + "/.config/signal";
        if (new File(legacySettingsPath).exists()) {
            return legacySettingsPath;
        }

        legacySettingsPath = System.getProperty("user.home") + "/.config/textsecure";
        if (new File(legacySettingsPath).exists()) {
            return legacySettingsPath;
        }

        return dataPath;
    }

    private static Namespace parseArgs(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("signal-cli")
                .build()
                .defaultHelp(true)
                .description("Commandline interface for Signal.")
                .version(BaseConfig.PROJECT_NAME + " " + BaseConfig.PROJECT_VERSION);

        parser.addArgument("-v", "--version")
                .help("Show package version.")
                .action(Arguments.version());
        parser.addArgument("--config")
                .help("Set the path, where to store the config (Default: $XDG_DATA_HOME/signal-cli , $HOME/.local/share/signal-cli).");

        MutuallyExclusiveGroup mut = parser.addMutuallyExclusiveGroup();
        mut.addArgument("-u", "--username")
                .help("Specify your phone number, that will be used for verification.");
        mut.addArgument("--singleuser")
                .help("Can be used if only one user account exists on this machine.")
                .action(Arguments.storeTrue());
        mut.addArgument("--dbus")
                .help("Make request via user dbus.")
                .action(Arguments.storeTrue());
        mut.addArgument("--dbus-system")
                .help("Make request via system dbus.")
                .action(Arguments.storeTrue());

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
        } else if (!ns.getBoolean("dbus") && !ns.getBoolean("dbus_system") && !ns.getBoolean("singleuser")) {
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
}
