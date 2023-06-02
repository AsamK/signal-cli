package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.SignalControl;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.CommandHandler;
import org.asamk.signal.commands.LocalCommand;
import org.asamk.signal.commands.MultiLocalCommand;
import org.asamk.signal.commands.ProvisioningCommand;
import org.asamk.signal.commands.RegistrationCommand;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.errors.UnknownMethod;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

public class DbusCommandHandler {

    public static void handleCommand(
            final Command command,
            final String account,
            final DBusConnection dBusConn,
            final CommandHandler commandHandler
    ) throws CommandException, DBusException {
        try {
            if (command instanceof ProvisioningCommand c) {
                if (account != null) {
                    throw new UserErrorException("You cannot specify a account (phone number) when linking");
                }

                handleProvisioningCommand(c, dBusConn, commandHandler);
                return;
            }

            if (account == null && command instanceof MultiLocalCommand c) {
                handleMultiLocalCommand(c, dBusConn, commandHandler);
                return;
            }
            if (account != null && command instanceof RegistrationCommand c) {
                handleRegistrationCommand(c, account, dBusConn, commandHandler);
                return;
            }
            if (!(command instanceof LocalCommand localCommand)) {
                throw new UserErrorException("Command only works in multi-account mode");
            }

            var accountObjectPath = account == null ? tryGetSingleAccountObjectPath(dBusConn) : null;
            if (accountObjectPath == null) {
                accountObjectPath = DbusConfig.getObjectPath(account);
            }
            handleLocalCommand(localCommand, accountObjectPath, dBusConn, commandHandler);
        } catch (UnsupportedOperationException e) {
            throw new UserErrorException("Command is not yet implemented via dbus", e);
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException(e.getMessage(), e);
        }
    }

    private static String tryGetSingleAccountObjectPath(final DBusConnection dBusConn) throws DBusException, CommandException {
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

    private static void handleMultiLocalCommand(
            final MultiLocalCommand c, final DBusConnection dBusConn, final CommandHandler commandHandler
    ) throws CommandException, DBusException {
        final var signalControl = dBusConn.getRemoteObject(DbusConfig.getBusname(),
                DbusConfig.getObjectPath(),
                SignalControl.class);
        try (final var multiAccountManager = new DbusMultiAccountManagerImpl(signalControl, dBusConn)) {
            commandHandler.handleMultiLocalCommand(c, multiAccountManager);
        }
    }

    private static void handleLocalCommand(
            final LocalCommand c,
            String accountObjectPath,
            final DBusConnection dBusConn,
            final CommandHandler commandHandler
    ) throws CommandException, DBusException {
        var signal = dBusConn.getRemoteObject(DbusConfig.getBusname(), accountObjectPath, Signal.class);
        try (final var manager = new DbusManagerImpl(signal, dBusConn)) {
            commandHandler.handleLocalCommand(c, manager);
        }
    }

    private static void handleRegistrationCommand(
            final RegistrationCommand c,
            String account,
            final DBusConnection dBusConn,
            final CommandHandler commandHandler

    ) throws CommandException, DBusException {
        final var signalControl = dBusConn.getRemoteObject(DbusConfig.getBusname(),
                DbusConfig.getObjectPath(),
                SignalControl.class);
        try (final var registrationManager = new DbusRegistrationManagerImpl(account, signalControl, dBusConn)) {
            commandHandler.handleRegistrationCommand(c, registrationManager);
        }
    }

    private static void handleProvisioningCommand(
            final ProvisioningCommand c, final DBusConnection dBusConn, final CommandHandler commandHandler

    ) throws CommandException, DBusException {
        final var signalControl = dBusConn.getRemoteObject(DbusConfig.getBusname(),
                DbusConfig.getObjectPath(),
                SignalControl.class);
        final var provisioningManager = new DbusProvisioningManagerImpl(signalControl, dBusConn);
        commandHandler.handleProvisioningCommand(c, provisioningManager);
    }
}
