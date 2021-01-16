package org.asamk.signal;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
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
import org.asamk.signal.commands.MultiLocalCommand;
import org.asamk.signal.commands.ProvisioningCommand;
import org.asamk.signal.commands.RegistrationCommand;
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
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Cli {

    private final static Logger logger = LoggerFactory.getLogger(Cli.class);

    private final Namespace ns;

    static ArgumentParser buildArgumentParser() {
        ArgumentParser parser = ArgumentParsers.newFor("signal-cli")
                .build()
                .defaultHelp(true)
                .description("Commandline interface for Signal.")
                .version(BaseConfig.PROJECT_NAME + " " + BaseConfig.PROJECT_VERSION);

        parser.addArgument("-v", "--version").help("Show package version.").action(Arguments.version());
        parser.addArgument("--verbose")
                .help("Raise log level and include lib signal logs.")
                .action(Arguments.storeTrue());
        parser.addArgument("--config")
                .help("Set the path, where to store the config (Default: $XDG_DATA_HOME/signal-cli , $HOME/.local/share/signal-cli).");

        parser.addArgument("-u", "--username").help("Specify your phone number, that will be used for verification.");

        MutuallyExclusiveGroup mut = parser.addMutuallyExclusiveGroup();
        mut.addArgument("--dbus").help("Make request via user dbus.").action(Arguments.storeTrue());
        mut.addArgument("--dbus-system").help("Make request via system dbus.").action(Arguments.storeTrue());

        parser.addArgument("-o", "--output")
                .help("Choose to output in plain text or JSON")
                .type(Arguments.enumStringType(OutputType.class))
                .setDefault(OutputType.PLAIN_TEXT);

        Subparsers subparsers = parser.addSubparsers().title("subcommands").dest("command");

        final Map<String, Command> commands = Commands.getCommands();
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            Subparser subparser = subparsers.addParser(entry.getKey());
            entry.getValue().attachToSubparser(subparser);
        }

        return parser;
    }

    public Cli(final Namespace ns) {
        this.ns = ns;
    }

    public int init() {
        String commandKey = ns.getString("command");
        Command command = Commands.getCommand(commandKey);
        if (command == null) {
            logger.error("Command not implemented!");
            return 1;
        }

        OutputType outputType = ns.get("output");
        if (!command.getSupportedOutputTypes().contains(outputType)) {
            logger.error("Command doesn't support output type {}", outputType.toString());
            return 1;
        }

        String username = ns.getString("username");

        final boolean useDbus = ns.getBoolean("dbus");
        final boolean useDbusSystem = ns.getBoolean("dbus_system");
        if (useDbus || useDbusSystem) {
            // If username is null, it will connect to the default object path
            return initDbusClient(command, username, useDbusSystem);
        }

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

        if (command instanceof ProvisioningCommand) {
            if (username != null) {
                System.err.println("You cannot specify a username (phone number) when linking");
                return 1;
            }

            return handleProvisioningCommand((ProvisioningCommand) command, dataPath, serviceConfiguration);
        }

        if (username == null) {
            List<String> usernames = Manager.getAllLocalUsernames(dataPath);
            if (usernames.size() == 0) {
                System.err.println("No local users found, you first need to register or link an account");
                return 1;
            }

            if (command instanceof MultiLocalCommand) {
                return handleMultiLocalCommand((MultiLocalCommand) command, dataPath, serviceConfiguration, usernames);
            }

            if (usernames.size() > 1) {
                System.err.println("Multiple users found, you need to specify a username (phone number) with -u");
                return 1;
            }

            username = usernames.get(0);
        } else if (!PhoneNumberFormatter.isValidNumber(username, null)) {
            System.err.println("Invalid username (phone number), make sure you include the country code.");
            return 1;
        }

        if (command instanceof RegistrationCommand) {
            return handleRegistrationCommand((RegistrationCommand) command, username, dataPath, serviceConfiguration);
        }

        if (!(command instanceof LocalCommand)) {
            System.err.println("Command only works via dbus");
            return 1;
        }

        return handleLocalCommand((LocalCommand) command, username, dataPath, serviceConfiguration);
    }

    private int handleProvisioningCommand(
            final ProvisioningCommand command,
            final File dataPath,
            final SignalServiceConfiguration serviceConfiguration
    ) {
        ProvisioningManager pm = new ProvisioningManager(dataPath, serviceConfiguration, BaseConfig.USER_AGENT);
        return command.handleCommand(ns, pm);
    }

    private int handleRegistrationCommand(
            final RegistrationCommand command,
            final String username,
            final File dataPath,
            final SignalServiceConfiguration serviceConfiguration
    ) {
        final RegistrationManager manager;
        try {
            manager = RegistrationManager.init(username, dataPath, serviceConfiguration, BaseConfig.USER_AGENT);
        } catch (Throwable e) {
            logger.error("Error loading or creating state file: {}", e.getMessage());
            return 2;
        }
        try (RegistrationManager m = manager) {
            return command.handleCommand(ns, m);
        } catch (IOException e) {
            logger.error("Cleanup failed", e);
            return 2;
        }
    }

    private int handleLocalCommand(
            final LocalCommand command,
            final String username,
            final File dataPath,
            final SignalServiceConfiguration serviceConfiguration
    ) {
        try (Manager m = loadManager(username, dataPath, serviceConfiguration)) {
            if (m == null) {
                return 2;
            }

            return command.handleCommand(ns, m);
        } catch (IOException e) {
            logger.error("Cleanup failed", e);
            return 2;
        }
    }

    private int handleMultiLocalCommand(
            final MultiLocalCommand command,
            final File dataPath,
            final SignalServiceConfiguration serviceConfiguration,
            final List<String> usernames
    ) {
        final List<Manager> managers = usernames.stream()
                .map(u -> loadManager(u, dataPath, serviceConfiguration))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int result = command.handleCommand(ns, managers);

        for (Manager m : managers) {
            try {
                m.close();
            } catch (IOException e) {
                logger.warn("Cleanup failed", e);
            }
        }
        return result;
    }

    private Manager loadManager(
            final String username, final File dataPath, final SignalServiceConfiguration serviceConfiguration
    ) {
        Manager manager;
        try {
            manager = Manager.init(username, dataPath, serviceConfiguration, BaseConfig.USER_AGENT);
        } catch (NotRegisteredException e) {
            logger.error("User " + username + " is not registered.");
            return null;
        } catch (Throwable e) {
            logger.error("Error loading state file for user " + username + ": {}", e.getMessage());
            return null;
        }

        try {
            manager.checkAccountState();
        } catch (IOException e) {
            logger.error("Error while checking account " + username + ": {}", e.getMessage());
            return null;
        }

        return manager;
    }

    private int initDbusClient(final Command command, final String username, final boolean systemBus) {
        try {
            DBusConnection.DBusBusType busType;
            if (systemBus) {
                busType = DBusConnection.DBusBusType.SYSTEM;
            } else {
                busType = DBusConnection.DBusBusType.SESSION;
            }
            try (DBusConnection dBusConn = DBusConnection.getConnection(busType)) {
                Signal ts = dBusConn.getRemoteObject(DbusConfig.getBusname(),
                        DbusConfig.getObjectPath(username),
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
