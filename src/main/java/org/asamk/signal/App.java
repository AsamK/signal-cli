package org.asamk.signal;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.asamk.SignalControl;
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
import org.asamk.signal.dbus.DbusMultiAccountManagerImpl;
import org.asamk.signal.dbus.DbusProvisioningManagerImpl;
import org.asamk.signal.dbus.DbusRegistrationManagerImpl;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.SignalAccountFiles;
import org.asamk.signal.manager.api.AccountCheckException;
import org.asamk.signal.manager.api.NotRegisteredException;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;
import org.asamk.signal.output.JsonWriterImpl;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriterImpl;
import org.asamk.signal.util.IOUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.errors.ServiceUnknown;
import org.freedesktop.dbus.errors.UnknownMethod;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Set;

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

        parser.addArgument("--version").help("Show package version.").action(Arguments.version());
        parser.addArgument("-v", "--verbose")
                .help("Raise log level and include lib signal logs. Specify multiple times for even more logs.")
                .action(Arguments.count());
        parser.addArgument("--log-file")
                .type(File.class)
                .help("Write log output to the given file. If --verbose is also given, the detailed logs will only be written to the log file.");
        parser.addArgument("--scrub-log")
                .action(Arguments.storeTrue())
                .help("Scrub possibly sensitive information from the log, like phone numbers and UUIDs.");
        parser.addArgument("-c", "--config")
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
        var writer = new BufferedWriter(new OutputStreamWriter(System.out, IOUtils.getConsoleCharset()));
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

        if (!ServiceConfig.isSignalClientAvailable()) {
            throw new UserErrorException("Missing required native library dependency: libsignal-client");
        }

        final File configPath;
        var config = ns.getString("config");
        if (config != null) {
            configPath = new File(config);
        } else {
            configPath = getDefaultConfigPath();
        }

        final var serviceEnvironmentCli = ns.<ServiceEnvironmentCli>get("service-environment");
        final var serviceEnvironment = serviceEnvironmentCli == ServiceEnvironmentCli.LIVE
                ? ServiceEnvironment.LIVE
                : ServiceEnvironment.STAGING;

        final var trustNewIdentityCli = ns.<TrustNewIdentityCli>get("trust-new-identities");
        final var trustNewIdentity = trustNewIdentityCli == TrustNewIdentityCli.ON_FIRST_USE
                ? TrustNewIdentity.ON_FIRST_USE
                : trustNewIdentityCli == TrustNewIdentityCli.ALWAYS ? TrustNewIdentity.ALWAYS : TrustNewIdentity.NEVER;

        final SignalAccountFiles signalAccountFiles;
        try {
            signalAccountFiles = new SignalAccountFiles(configPath,
                    serviceEnvironment,
                    BaseConfig.USER_AGENT,
                    trustNewIdentity);
        } catch (IOException e) {
            throw new IOErrorException("Failed to read local accounts list", e);
        }

        if (command instanceof ProvisioningCommand provisioningCommand) {
            if (account != null) {
                throw new UserErrorException("You cannot specify a account (phone number) when linking");
            }

            handleProvisioningCommand(provisioningCommand, signalAccountFiles, outputWriter);
            return;
        }

        if (account == null) {
            if (command instanceof MultiLocalCommand multiLocalCommand) {
                handleMultiLocalCommand(multiLocalCommand, signalAccountFiles, outputWriter);
                return;
            }

            Set<String> accounts = null;
            try {
                accounts = signalAccountFiles.getAllLocalAccountNumbers();
            } catch (IOException e) {
                throw new IOErrorException("Failed to load local accounts file", e);
            }
            if (accounts.size() == 0) {
                throw new UserErrorException("No local users found, you first need to register or link an account");
            } else if (accounts.size() > 1) {
                throw new UserErrorException(
                        "Multiple users found, you need to specify an account (phone number) with -a");
            }

            account = accounts.stream().findFirst().get();
        } else if (!Manager.isValidNumber(account, null)) {
            throw new UserErrorException("Invalid account (phone number), make sure you include the country code.");
        }

        if (command instanceof RegistrationCommand registrationCommand) {
            handleRegistrationCommand(registrationCommand, account, signalAccountFiles);
            return;
        }

        if (!(command instanceof LocalCommand)) {
            throw new UserErrorException("Command only works in multi-account mode");
        }

        handleLocalCommand((LocalCommand) command, account, signalAccountFiles, outputWriter);
    }

    private void handleProvisioningCommand(
            final ProvisioningCommand command,
            final SignalAccountFiles signalAccountFiles,
            final OutputWriter outputWriter
    ) throws CommandException {
        var pm = signalAccountFiles.initProvisioningManager();
        command.handleCommand(ns, pm, outputWriter);
    }

    private void handleProvisioningCommand(
            final ProvisioningCommand c, final DBusConnection dBusConn, final OutputWriter outputWriter
    ) throws CommandException, DBusException {
        final var signalControl = dBusConn.getRemoteObject(DbusConfig.getBusname(),
                DbusConfig.getObjectPath(),
                SignalControl.class);
        final var provisioningManager = new DbusProvisioningManagerImpl(signalControl, dBusConn);
        try {
            c.handleCommand(ns, provisioningManager, outputWriter);
        } catch (UnsupportedOperationException e) {
            throw new UserErrorException("Command is not yet implemented via dbus", e);
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException(e.getMessage(), e);
        }
    }

    private void handleRegistrationCommand(
            final RegistrationCommand command, final String account, final SignalAccountFiles signalAccountFiles
    ) throws CommandException {
        try (final var manager = loadRegistrationManager(account, signalAccountFiles)) {
            command.handleCommand(ns, manager);
        } catch (IOException e) {
            logger.warn("Cleanup failed", e);
        }
    }

    private void handleRegistrationCommand(
            final RegistrationCommand c, String account, final DBusConnection dBusConn, final OutputWriter outputWriter
    ) throws CommandException, DBusException {
        final var signalControl = dBusConn.getRemoteObject(DbusConfig.getBusname(),
                DbusConfig.getObjectPath(),
                SignalControl.class);
        try (final var registrationManager = new DbusRegistrationManagerImpl(account, signalControl, dBusConn)) {
            c.handleCommand(ns, registrationManager);
        } catch (UnsupportedOperationException e) {
            throw new UserErrorException("Command is not yet implemented via dbus", e);
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException(e.getMessage(), e);
        }
    }

    private void handleLocalCommand(
            final LocalCommand command,
            final String account,
            final SignalAccountFiles signalAccountFiles,
            final OutputWriter outputWriter
    ) throws CommandException {
        try (var m = loadManager(account, signalAccountFiles)) {
            command.handleCommand(ns, m, outputWriter);
        } catch (IOException e) {
            logger.warn("Cleanup failed", e);
        }
    }

    private void handleLocalCommand(
            final LocalCommand c,
            String accountObjectPath,
            final DBusConnection dBusConn,
            final OutputWriter outputWriter
    ) throws CommandException, DBusException {
        var signal = dBusConn.getRemoteObject(DbusConfig.getBusname(), accountObjectPath, Signal.class);
        try (final var m = new DbusManagerImpl(signal, dBusConn)) {
            c.handleCommand(ns, m, outputWriter);
        } catch (UnsupportedOperationException e) {
            throw new UserErrorException("Command is not yet implemented via dbus", e);
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException(e.getMessage(), e);
        }
    }

    private void handleMultiLocalCommand(
            final MultiLocalCommand command,
            final SignalAccountFiles signalAccountFiles,
            final OutputWriter outputWriter
    ) throws CommandException {
        try (var multiAccountManager = signalAccountFiles.initMultiAccountManager()) {
            command.handleCommand(ns, multiAccountManager, outputWriter);
        } catch (IOException e) {
            throw new IOErrorException("Failed to load local accounts file", e);
        }
    }

    private void handleMultiLocalCommand(
            final MultiLocalCommand c, final DBusConnection dBusConn, final OutputWriter outputWriter
    ) throws CommandException, DBusException {
        final var signalControl = dBusConn.getRemoteObject(DbusConfig.getBusname(),
                DbusConfig.getObjectPath(),
                SignalControl.class);
        try (final var multiAccountManager = new DbusMultiAccountManagerImpl(signalControl, dBusConn)) {
            c.handleCommand(ns, multiAccountManager, outputWriter);
        } catch (UnsupportedOperationException e) {
            throw new UserErrorException("Command is not yet implemented via dbus", e);
        }
    }

    private RegistrationManager loadRegistrationManager(
            final String account, final SignalAccountFiles signalAccountFiles
    ) throws UnexpectedErrorException {
        try {
            return signalAccountFiles.initRegistrationManager(account);
        } catch (Throwable e) {
            throw new UnexpectedErrorException("Error loading or creating state file: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        }
    }

    private Manager loadManager(
            final String account, final SignalAccountFiles signalAccountFiles
    ) throws CommandException {
        logger.trace("Loading account file for {}", account);
        try {
            return signalAccountFiles.initManager(account);
        } catch (NotRegisteredException e) {
            throw new UserErrorException("User " + account + " is not registered.");
        } catch (AccountCheckException ace) {
            if (ace.getCause() instanceof IOException e) {
                throw new IOErrorException("Error while checking account " + account + ": " + e.getMessage(), e);
            } else {
                throw new UnexpectedErrorException("Error while checking account " + account + ": " + ace.getMessage(),
                        ace);
            }
        } catch (Throwable e) {
            throw new UnexpectedErrorException("Error loading state file for user "
                    + account
                    + ": "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        }
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
            try (var dBusConn = DBusConnectionBuilder.forType(busType).build()) {
                if (command instanceof ProvisioningCommand c) {
                    if (account != null) {
                        throw new UserErrorException("You cannot specify a account (phone number) when linking");
                    }

                    handleProvisioningCommand(c, dBusConn, outputWriter);
                    return;
                }

                if (account == null && command instanceof MultiLocalCommand c) {
                    handleMultiLocalCommand(c, dBusConn, outputWriter);
                    return;
                }
                if (account != null && command instanceof RegistrationCommand c) {
                    handleRegistrationCommand(c, account, dBusConn, outputWriter);
                    return;
                }
                if (!(command instanceof LocalCommand localCommand)) {
                    throw new UserErrorException("Command only works in multi-account mode");
                }

                var accountObjectPath = account == null ? tryGetSingleAccountObjectPath(dBusConn) : null;
                if (accountObjectPath == null) {
                    accountObjectPath = DbusConfig.getObjectPath(account);
                }
                handleLocalCommand(localCommand, accountObjectPath, dBusConn, outputWriter);
            }
        } catch (ServiceUnknown e) {
            throw new UserErrorException("signal-cli DBus daemon not running on "
                    + (systemBus ? "system" : "session")
                    + " bus: "
                    + e.getMessage(), e);
        } catch (DBusExecutionException | DBusException | IOException e) {
            throw new UnexpectedErrorException("Dbus client failed: " + e.getMessage(), e);
        }
    }

    private String tryGetSingleAccountObjectPath(final DBusConnection dBusConn) throws DBusException, CommandException {
        var control = dBusConn.getRemoteObject(DbusConfig.getBusname(),
                DbusConfig.getObjectPath(),
                SignalControl.class);
        try {
            final var accounts = control.listAccounts();
            if (accounts.size() == 0) {
                throw new UserErrorException("No local users found, you first need to register or link an account");
            } else if (accounts.size() > 1) {
                throw new UserErrorException(
                        "Multiple users found, you need to specify an account (phone number) with -a");
            }

            return accounts.get(0).getPath();
        } catch (UnknownMethod e) {
            // dbus daemon not running in multi-account mode
            return null;
        }
    }

    /**
     * @return the default data directory to be used by signal-cli.
     */
    private static File getDefaultConfigPath() {
        return new File(IOUtils.getDataHomeDir(), "signal-cli");
    }
}
