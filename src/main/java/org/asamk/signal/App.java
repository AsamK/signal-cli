package org.asamk.signal;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.LocalCommand;
import org.asamk.signal.commands.MultiLocalCommand;
import org.asamk.signal.commands.ProvisioningCommand;
import org.asamk.signal.commands.RegistrationCommand;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.dbus.DbusManagerImpl;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManagerImpl;
import org.asamk.signal.manager.NotRegisteredException;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;
import org.asamk.signal.util.IOUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
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

        parser.addArgument("-a", "--account", "-u", "--username")
                .help("Specify your phone number, that will be your identifier.");

        var mut = parser.addMutuallyExclusiveGroup();
        mut.addArgument("--dbus").dest("global-dbus").help("Make request via user dbus.").action(Arguments.storeTrue());
        mut.addArgument("--dbus-system")
                .dest("global-dbus-system")
                .help("Make request via system dbus.")
                .action(Arguments.storeTrue());

        parser.addArgument("-o", "--output")
                .help("Choose to output in plain text or JSON")
                .type(Arguments.enumStringType(OutputType.class));

        parser.addArgument("--service-environment")
                .help("Choose the server environment to use.")
                .type(Arguments.enumStringType(ServiceEnvironmentCli.class))
                .setDefault(ServiceEnvironmentCli.LIVE);

        parser.addArgument("--trust-new-identities")
                .help("Choose when to trust new identities.")
                .type(Arguments.enumStringType(TrustNewIdentityCli.class))
                .setDefault(TrustNewIdentityCli.ON_FIRST_USE);

        var subparsers = parser.addSubparsers().title("subcommands").dest("command");

        Commands.getCommandSubparserAttachers().forEach((key, value) -> {
            var subparser = subparsers.addParser(key);
            value.attachToSubparser(subparser);
        });

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

        var outputTypeInput = ns.<OutputType>get("output");
        var outputType = outputTypeInput == null
                ? command.getSupportedOutputTypes().stream().findFirst().orElse(null)
                : outputTypeInput;
        var writer = new BufferedWriter(new OutputStreamWriter(System.out, Charset.defaultCharset()));
        var outputWriter = outputType == null
                ? null
                : outputType == OutputType.JSON ? new JsonWriterImpl(writer) : new PlainTextWriterImpl(writer);

        if (outputWriter != null && !command.getSupportedOutputTypes().contains(outputType)) {
            throw new UserErrorException("Command doesn't support output type " + outputType);
        }

        var account = ns.getString("account");

        final var useDbus = Boolean.TRUE.equals(ns.getBoolean("global-dbus"));
        final var useDbusSystem = Boolean.TRUE.equals(ns.getBoolean("global-dbus-system"));
        if (useDbus || useDbusSystem) {
            // If account is null, it will connect to the default object path
            initDbusClient(command, account, useDbusSystem, outputWriter);
            return;
        }

        final File dataPath;
        var config = ns.getString("config");
        if (config != null) {
            dataPath = new File(config);
        } else {
            dataPath = getDefaultDataPath();
        }

        if (!ServiceConfig.isZkgroupAvailable()) {
            logger.warn("WARNING: Support for new group V2 is disabled,"
                    + " because the required native library dependency is missing: libzkgroup");
        }

        if (!ServiceConfig.isSignalClientAvailable()) {
            throw new UserErrorException("Missing required native library dependency: libsignal-client");
        }

        final var serviceEnvironmentCli = ns.<ServiceEnvironmentCli>get("service-environment");
        final var serviceEnvironment = serviceEnvironmentCli == ServiceEnvironmentCli.LIVE
                ? ServiceEnvironment.LIVE
                : ServiceEnvironment.SANDBOX;

        final var trustNewIdentityCli = ns.<TrustNewIdentityCli>get("trust-new-identities");
        final var trustNewIdentity = trustNewIdentityCli == TrustNewIdentityCli.ON_FIRST_USE
                ? TrustNewIdentity.ON_FIRST_USE
                : trustNewIdentityCli == TrustNewIdentityCli.ALWAYS ? TrustNewIdentity.ALWAYS : TrustNewIdentity.NEVER;

        if (command instanceof ProvisioningCommand provisioningCommand) {
            if (account != null) {
                throw new UserErrorException("You cannot specify a account (phone number) when linking");
            }

            handleProvisioningCommand(provisioningCommand, dataPath, serviceEnvironment, outputWriter);
            return;
        }

        if (account == null) {
            var accounts = Manager.getAllLocalAccountNumbers(dataPath);

            if (command instanceof MultiLocalCommand multiLocalCommand) {
                handleMultiLocalCommand(multiLocalCommand,
                        dataPath,
                        serviceEnvironment,
                        accounts,
                        outputWriter,
                        trustNewIdentity);
                return;
            }

            if (accounts.size() == 0) {
                throw new UserErrorException("No local users found, you first need to register or link an account");
            } else if (accounts.size() > 1) {
                throw new UserErrorException(
                        "Multiple users found, you need to specify an account (phone number) with -a");
            }

            account = accounts.get(0);
        } else if (!Manager.isValidNumber(account, null)) {
            throw new UserErrorException("Invalid account (phone number), make sure you include the country code.");
        }

        if (command instanceof RegistrationCommand registrationCommand) {
            handleRegistrationCommand(registrationCommand, account, dataPath, serviceEnvironment);
            return;
        }

        if (!(command instanceof LocalCommand)) {
            throw new UserErrorException("Command only works via dbus");
        }

        handleLocalCommand((LocalCommand) command,
                account,
                dataPath,
                serviceEnvironment,
                outputWriter,
                trustNewIdentity);
    }

    private void handleProvisioningCommand(
            final ProvisioningCommand command,
            final File dataPath,
            final ServiceEnvironment serviceEnvironment,
            final OutputWriter outputWriter
    ) throws CommandException {
        var pm = ProvisioningManager.init(dataPath, serviceEnvironment, BaseConfig.USER_AGENT);
        command.handleCommand(ns, pm, outputWriter);
    }

    private void handleRegistrationCommand(
            final RegistrationCommand command,
            final String account,
            final File dataPath,
            final ServiceEnvironment serviceEnvironment
    ) throws CommandException {
        final RegistrationManager manager;
        try {
            manager = RegistrationManager.init(account, dataPath, serviceEnvironment, BaseConfig.USER_AGENT);
        } catch (Throwable e) {
            throw new UnexpectedErrorException("Error loading or creating state file: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        }
        try (manager) {
            command.handleCommand(ns, manager);
        } catch (IOException e) {
            logger.warn("Cleanup failed", e);
        }
    }

    private void handleLocalCommand(
            final LocalCommand command,
            final String account,
            final File dataPath,
            final ServiceEnvironment serviceEnvironment,
            final OutputWriter outputWriter,
            final TrustNewIdentity trustNewIdentity
    ) throws CommandException {
        try (var m = loadManager(account, dataPath, serviceEnvironment, trustNewIdentity)) {
            command.handleCommand(ns, m, outputWriter);
        } catch (IOException e) {
            logger.warn("Cleanup failed", e);
        }
    }

    private void handleMultiLocalCommand(
            final MultiLocalCommand command,
            final File dataPath,
            final ServiceEnvironment serviceEnvironment,
            final List<String> accounts,
            final OutputWriter outputWriter,
            final TrustNewIdentity trustNewIdentity
    ) throws CommandException {
        final var managers = new ArrayList<Manager>();
        for (String a : accounts) {
            try {
                managers.add(loadManager(a, dataPath, serviceEnvironment, trustNewIdentity));
            } catch (CommandException e) {
                logger.warn("Ignoring {}: {}", a, e.getMessage());
            }
        }

        try (var multiAccountManager = new MultiAccountManagerImpl(managers,
                dataPath,
                serviceEnvironment,
                BaseConfig.USER_AGENT)) {
            command.handleCommand(ns, multiAccountManager, outputWriter);
        }
    }

    private Manager loadManager(
            final String account,
            final File dataPath,
            final ServiceEnvironment serviceEnvironment,
            final TrustNewIdentity trustNewIdentity
    ) throws CommandException {
        Manager manager;
        try {
            manager = Manager.init(account, dataPath, serviceEnvironment, BaseConfig.USER_AGENT, trustNewIdentity);
        } catch (NotRegisteredException e) {
            throw new UserErrorException("User " + account + " is not registered.");
        } catch (Throwable e) {
            throw new UnexpectedErrorException("Error loading state file for user "
                    + account
                    + ": "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        }

        try {
            manager.checkAccountState();
        } catch (IOException e) {
            try {
                manager.close();
            } catch (IOException ie) {
                logger.warn("Failed to close broken account", ie);
            }
            throw new IOErrorException("Error while checking account " + account + ": " + e.getMessage(), e);
        }

        return manager;
    }

    private void initDbusClient(
            final Command command, final String account, final boolean systemBus, final OutputWriter outputWriter
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
                        DbusConfig.getObjectPath(account),
                        Signal.class);

                handleCommand(command, ts, dBusConn, outputWriter);
            }
        } catch (DBusException | IOException e) {
            logger.error("Dbus client failed", e);
            throw new UnexpectedErrorException("Dbus client failed", e);
        }
    }

    private void handleCommand(
            Command command, Signal ts, DBusConnection dBusConn, OutputWriter outputWriter
    ) throws CommandException {
        if (command instanceof LocalCommand localCommand) {
            try (final var m = new DbusManagerImpl(ts, dBusConn)) {
                localCommand.handleCommand(ns, m, outputWriter);
            } catch (UnsupportedOperationException e) {
                throw new UserErrorException("Command is not yet implemented via dbus", e);
            } catch (IOException | DBusExecutionException e) {
                throw new UnexpectedErrorException(e.getMessage(), e);
            }
        } else {
            throw new UserErrorException("Command is not yet implemented via dbus");
        }
    }

    /**
     * @return the default data directory to be used by signal-cli.
     */
    private static File getDefaultDataPath() {
        return new File(IOUtils.getDataHomeDir(), "signal-cli");
    }
}
