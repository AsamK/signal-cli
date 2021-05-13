package org.asamk.signal;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.DbusCommand;
import org.asamk.signal.commands.ExtendedDbusCommand;
import org.asamk.signal.commands.LocalCommand;
import org.asamk.signal.commands.MultiLocalCommand;
import org.asamk.signal.commands.ProvisioningCommand;
import org.asamk.signal.commands.RegistrationCommand;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotRegisteredException;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.util.IOUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static net.sourceforge.argparse4j.DefaultSettings.VERSION_0_9_0_DEFAULT_SETTINGS;

public class App {

    private final static Logger logger = LoggerFactory.getLogger(App.class);

    private final Namespace ns;

    static ArgumentParser buildArgumentParser() {
        var parser = ArgumentParsers.newFor("signal-cli", VERSION_0_9_0_DEFAULT_SETTINGS)
                .includeArgumentNamesAsKeysInResult(true)
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

        parser.addArgument("-u", "--username").help("Specify your phone number, that will be your identifier.");

        var mut = parser.addMutuallyExclusiveGroup();
        mut.addArgument("--dbus").help("Make request via user dbus.").action(Arguments.storeTrue());
        mut.addArgument("--dbus-system").help("Make request via system dbus.").action(Arguments.storeTrue());

        parser.addArgument("-o", "--output")
                .help("Choose to output in plain text or JSON")
                .type(Arguments.enumStringType(OutputType.class))
                .setDefault(OutputType.PLAIN_TEXT);

        parser.addArgument("--service-environment")
                .help("Choose the server environment to use, SANDBOX or LIVE.")
                .type(Arguments.enumStringType(ServiceEnvironmentCli.class))
                .setDefault(ServiceEnvironmentCli.LIVE);

        var subparsers = parser.addSubparsers().title("subcommands").dest("command");

        final var commands = Commands.getCommands();
        for (var entry : commands.entrySet()) {
            var subparser = subparsers.addParser(entry.getKey());
            entry.getValue().attachToSubparser(subparser);
        }

        return parser;
    }

    public App(final Namespace ns) {
        this.ns = ns;
    }

    public void init() throws CommandException {
        var commandKey = ns.getString("command");
        var command = Commands.getCommand(commandKey);
        if (command == null) {
            throw new UserErrorException("Command not implemented!");
        }

        var outputType = ns.<OutputType>get("output");
        if (!command.getSupportedOutputTypes().contains(outputType)) {
            throw new UserErrorException("Command doesn't support output type " + outputType.toString());
        }

        var username = ns.getString("username");

        final var useDbus = ns.getBoolean("dbus");
        final var useDbusSystem = ns.getBoolean("dbus-system");
        if (useDbus || useDbusSystem) {
            // If username is null, it will connect to the default object path
            initDbusClient(command, username, useDbusSystem);
            return;
        }

        final File dataPath;
        var config = ns.getString("config");
        if (config != null) {
            dataPath = new File(config);
        } else {
            dataPath = getDefaultDataPath();
        }

        final var serviceEnvironmentCli = ns.<ServiceEnvironmentCli>get("service-environment");
        final var serviceEnvironment = serviceEnvironmentCli == ServiceEnvironmentCli.LIVE
                ? ServiceEnvironment.LIVE
                : ServiceEnvironment.SANDBOX;

        if (!ServiceConfig.getCapabilities().isGv2()) {
            logger.warn("WARNING: Support for new group V2 is disabled,"
                    + " because the required native library dependency is missing: libzkgroup");
        }

        if (!ServiceConfig.isSignalClientAvailable()) {
            throw new UserErrorException("Missing required native library dependency: libsignal-client");
        }

        if (command instanceof ProvisioningCommand) {
            if (username != null) {
                throw new UserErrorException("You cannot specify a username (phone number) when linking");
            }

            handleProvisioningCommand((ProvisioningCommand) command, dataPath, serviceEnvironment);
            return;
        }

        if (username == null) {
            var usernames = Manager.getAllLocalUsernames(dataPath);

            if (command instanceof MultiLocalCommand) {
                handleMultiLocalCommand((MultiLocalCommand) command, dataPath, serviceEnvironment, usernames);
                return;
            }

            if (usernames.size() == 0) {
                throw new UserErrorException("No local users found, you first need to register or link an account");
            } else if (usernames.size() > 1) {
                throw new UserErrorException(
                        "Multiple users found, you need to specify a username (phone number) with -u");
            }

            username = usernames.get(0);
        } else if (!PhoneNumberFormatter.isValidNumber(username, null)) {
            throw new UserErrorException("Invalid username (phone number), make sure you include the country code.");
        }

        if (command instanceof RegistrationCommand) {
            handleRegistrationCommand((RegistrationCommand) command, username, dataPath, serviceEnvironment);
            return;
        }

        if (!(command instanceof LocalCommand)) {
            throw new UserErrorException("Command only works via dbus");
        }

        handleLocalCommand((LocalCommand) command, username, dataPath, serviceEnvironment);
    }

    private void handleProvisioningCommand(
            final ProvisioningCommand command, final File dataPath, final ServiceEnvironment serviceEnvironment
    ) throws CommandException {
        var pm = ProvisioningManager.init(dataPath, serviceEnvironment, BaseConfig.USER_AGENT);
        command.handleCommand(ns, pm);
    }

    private void handleRegistrationCommand(
            final RegistrationCommand command,
            final String username,
            final File dataPath,
            final ServiceEnvironment serviceEnvironment
    ) throws CommandException {
        final RegistrationManager manager;
        try {
            manager = RegistrationManager.init(username, dataPath, serviceEnvironment, BaseConfig.USER_AGENT);
        } catch (Throwable e) {
            throw new UnexpectedErrorException("Error loading or creating state file: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")");
        }
        try (var m = manager) {
            command.handleCommand(ns, m);
        } catch (IOException e) {
            logger.warn("Cleanup failed", e);
        }
    }

    private void handleLocalCommand(
            final LocalCommand command,
            final String username,
            final File dataPath,
            final ServiceEnvironment serviceEnvironment
    ) throws CommandException {
        try (var m = loadManager(username, dataPath, serviceEnvironment)) {
            command.handleCommand(ns, m);
        } catch (IOException e) {
            logger.warn("Cleanup failed", e);
        }
    }

    private void handleMultiLocalCommand(
            final MultiLocalCommand command,
            final File dataPath,
            final ServiceEnvironment serviceEnvironment,
            final List<String> usernames
    ) throws CommandException {
        final var managers = new ArrayList<Manager>();
        for (String u : usernames) {
            try {
                managers.add(loadManager(u, dataPath, serviceEnvironment));
            } catch (CommandException e) {
                logger.warn("Ignoring {}: {}", u, e.getMessage());
            }
        }

        command.handleCommand(ns, managers);

        for (var m : managers) {
            try {
                m.close();
            } catch (IOException e) {
                logger.warn("Cleanup failed", e);
            }
        }
    }

    private Manager loadManager(
            final String username, final File dataPath, final ServiceEnvironment serviceEnvironment
    ) throws CommandException {
        Manager manager;
        try {
            manager = Manager.init(username, dataPath, serviceEnvironment, BaseConfig.USER_AGENT);
        } catch (NotRegisteredException e) {
            throw new UserErrorException("User " + username + " is not registered.");
        } catch (Throwable e) {
            logger.debug("Loading state file failed", e);
            throw new UnexpectedErrorException("Error loading state file for user "
                    + username
                    + ": "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")");
        }

        try {
            manager.checkAccountState();
        } catch (IOException e) {
            throw new UnexpectedErrorException("Error while checking account " + username + ": " + e.getMessage());
        }

        return manager;
    }

    private void initDbusClient(
            final Command command, final String username, final boolean systemBus
    ) throws CommandException {
        try {
            DBusConnection.DBusBusType busType;
            if (systemBus) {
                busType = DBusConnection.DBusBusType.SYSTEM;
            } else {
                busType = DBusConnection.DBusBusType.SESSION;
            }
            try (var dBusConn = DBusConnection.getConnection(busType)) {
                var ts = dBusConn.getRemoteObject(DbusConfig.getBusname(),
                        DbusConfig.getObjectPath(username),
                        Signal.class);

                handleCommand(command, ts, dBusConn);
            }
        } catch (DBusException | IOException e) {
            logger.error("Dbus client failed", e);
            throw new UnexpectedErrorException("Dbus client failed");
        }
    }

    private void handleCommand(Command command, Signal ts, DBusConnection dBusConn) throws CommandException {
        if (command instanceof ExtendedDbusCommand) {
            ((ExtendedDbusCommand) command).handleCommand(ns, ts, dBusConn);
        } else if (command instanceof DbusCommand) {
            ((DbusCommand) command).handleCommand(ns, ts);
        } else {
            throw new UserErrorException("Command is not yet implemented via dbus");
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
        var dataPath = new File(IOUtils.getDataHomeDir(), "signal-cli");
        if (dataPath.exists()) {
            return dataPath;
        }

        var configPath = new File(System.getProperty("user.home"), ".config");

        var legacySettingsPath = new File(configPath, "signal");
        if (legacySettingsPath.exists()) {
            logger.warn("Using legacy data path \"{}\", please move it to \"{}\".", legacySettingsPath, dataPath);
            return legacySettingsPath;
        }

        legacySettingsPath = new File(configPath, "textsecure");
        if (legacySettingsPath.exists()) {
            logger.warn("Using legacy data path \"{}\", please move it to \"{}\".", legacySettingsPath, dataPath);
            return legacySettingsPath;
        }

        return dataPath;
    }
}
