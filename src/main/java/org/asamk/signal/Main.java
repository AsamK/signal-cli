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
import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.DbusCommand;
import org.asamk.signal.commands.ExtendedDbusCommand;
import org.asamk.signal.commands.LocalCommand;
import org.asamk.signal.manager.BaseConfig;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.IOUtils;
import org.asamk.signal.util.SecurityProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.File;
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
        final String username = ns.getString("username");
        Manager m;
        Signal ts;
        DBusConnection dBusConn = null;
        try {
            if (ns.getBoolean("dbus") || ns.getBoolean("dbus_system")) {
                try {
                    m = null;
                    int busType;
                    if (ns.getBoolean("dbus_system")) {
                        busType = DBusConnection.SYSTEM;
                    } else {
                        busType = DBusConnection.SESSION;
                    }
                    dBusConn = DBusConnection.getConnection(busType);
                    ts = dBusConn.getRemoteObject(
                            DbusConfig.SIGNAL_BUSNAME, DbusConfig.SIGNAL_OBJECTPATH,
                            Signal.class);
                } catch (UnsatisfiedLinkError e) {
                    System.err.println("Missing native library dependency for dbus service: " + e.getMessage());
                    return 1;
                } catch (DBusException e) {
                    e.printStackTrace();
                    if (dBusConn != null) {
                        dBusConn.disconnect();
                    }
                    return 3;
                }
            } else {
                String dataPath = ns.getString("config");
                if (isEmpty(dataPath)) {
                    dataPath = getDefaultDataPath();
                }

                m = new Manager(username, dataPath);
                ts = m;
                try {
                    m.init();
                } catch (Exception e) {
                    System.err.println("Error loading state file: " + e.getMessage());
                    return 2;
                }
            }

            String commandKey = ns.getString("command");
            final Map<String, Command> commands = Commands.getCommands();
            if (commands.containsKey(commandKey)) {
                Command command = commands.get(commandKey);

                if (dBusConn != null) {
                    if (command instanceof ExtendedDbusCommand) {
                        return ((ExtendedDbusCommand) command).handleCommand(ns, ts, dBusConn);
                    } else if (command instanceof DbusCommand) {
                        return ((DbusCommand) command).handleCommand(ns, ts);
                    } else {
                        System.err.println(commandKey + " is not yet implemented via dbus");
                        return 1;
                    }
                } else {
                    if (command instanceof LocalCommand) {
                        return ((LocalCommand) command).handleCommand(ns, m);
                    } else if (command instanceof DbusCommand) {
                        return ((DbusCommand) command).handleCommand(ns, ts);
                    } else {
                        System.err.println(commandKey + " is only works via dbus");
                        return 1;
                    }
                }
            }
            return 0;
        } finally {
            if (dBusConn != null) {
                dBusConn.disconnect();
            }
        }
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
}
