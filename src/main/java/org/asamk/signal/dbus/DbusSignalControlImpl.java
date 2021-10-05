package org.asamk.signal.dbus;

import org.asamk.SignalControl;
import org.asamk.SignalControl.Error;
import org.asamk.signal.App;
import org.asamk.signal.BaseConfig;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.DbusReceiveMessageHandler;
import org.asamk.signal.JsonDbusReceiveMessageHandler;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.DaemonCommand;
import org.asamk.signal.commands.SignalCreator;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.UserAlreadyExists;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DbusSignalControlImpl implements org.asamk.SignalControl {

    private static SignalCreator c;
    private static Function<Manager, Thread> newManagerRunner;

    private static List<Pair<Manager, Thread>> receiveThreads = new ArrayList<>();
    private static Object stopTrigger = new Object();
    private static String objectPath;
    private static DBusConnection.DBusBusType busType;
    public static RegistrationManager registrationManager;
    public static ProvisioningManager provisioningManager;

    private final static Logger logger = LoggerFactory.getLogger(DbusSignalControlImpl.class);

    public DbusSignalControlImpl(
            final SignalCreator c, final Function<Manager, Thread> newManagerRunner, final String objectPath
    ) {
        this.c = c;
        this.newManagerRunner = newManagerRunner;
        this.objectPath = objectPath;
        this.busType = busType;
    }

    public static void addManager(Manager m) {
        var thread = newManagerRunner.apply(m);
        if (thread == null) {
            return;
        }
        synchronized (receiveThreads) {
            receiveThreads.add(new Pair<>(m, thread));
        }
    }

    public void run() {
        synchronized (stopTrigger) {
            try {
                stopTrigger.wait();
            } catch (InterruptedException ignored) {
            }
        }

        synchronized (receiveThreads) {
            for (var t : receiveThreads) {
                t.second().interrupt();
            }
        }
        while (true) {
            final Thread thread;
            synchronized (receiveThreads) {
                if (receiveThreads.size() == 0) {
                    break;
                }
                var pair = receiveThreads.remove(0);
                thread = pair.second();
            }
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public String getObjectPath() {
        return objectPath;
    }

    @Override
    public void register(
            final String number, final boolean voiceVerification
    ) throws Error.Failure, Error.InvalidNumber {
        registerWithCaptcha(number, voiceVerification, null);
    }

    @Override
    public void registerWithCaptcha(
            final String number, final boolean voiceVerification, final String captcha
    ) throws Error.Failure, Error.InvalidNumber {
        if (!PhoneNumberFormatter.isValidNumber(number, null)) {
            throw new SignalControl.Error.InvalidNumber(
                    "Invalid username (phone number), make sure you include the country code.");
        }
        try (final RegistrationManager registrationManager = c.getNewRegistrationManager(number)) {
            registrationManager.register(voiceVerification, captcha);
        } catch (CaptchaRequiredException e) {
            String message = captcha == null ? "Captcha required for verification." : "Invalid captcha given.";
            throw new SignalControl.Error.RequiresCaptcha(message);
        } catch (IOException e) {
            throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    @Override
    public void verify(final String number, final String verificationCode) throws Error.Failure, Error.InvalidNumber {
        verifyWithPin(number, verificationCode, null);
    }

    @Override
    public void verifyWithPin(
            final String number, final String verificationCode, final String pin
    ) throws Error.Failure, Error.InvalidNumber {
        try (final RegistrationManager registrationManager = c.getNewRegistrationManager(number)) {
            final Manager manager = registrationManager.verifyAccount(verificationCode, pin);
            logger.info("Registration of " + number + " verified");
            addManager(manager);
            registrationManager.close();
        } catch (IOException | KeyBackupSystemNoDataException | KeyBackupServicePinException e) {
            throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    @Override
    public String link(final String newDeviceName) throws Error.Failure {
        try {
            final ProvisioningManager provisioningManager = c.getNewProvisioningManager();
            final URI deviceLinkUri = provisioningManager.getDeviceLinkUri();
            new Thread(() -> {
                try {
                    final Manager manager = provisioningManager.finishDeviceLink(newDeviceName);
                    logger.info("Linking of " + newDeviceName + " successful");
                    addManager(manager);
                    //no need to close provisioningManager; it cleaned up during finishDeviceLink
                } catch (TimeoutException e) {
                    throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + ": Link request timed out, please try again.");
                } catch (IOException e) {
                    throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + ": Link request error: " + e.getMessage());
                } catch (UserAlreadyExists e) {
                    throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + ": The user "
                            + e.getNumber()
                            + " already exists\nDelete \""
                            + e.getFileName()
                            + "\" before trying again.");
                }
            }).start();
            return deviceLinkUri.toString();
        } catch (TimeoutException | IOException e) {
            throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    @Override
    public String version() {
        return BaseConfig.PROJECT_VERSION;
    }

    @Override
    public void listen(String number) {
        try {
            File settingsPath = c.getSettingsPath();
            List<String> usernames = Manager.getAllLocalNumbers(settingsPath);
            if (!usernames.contains(number)) {
                throw new Error.Failure("Listen: " + number + " is not registered.");
            }
            String objectPath = DbusConfig.getObjectPath(number);
            DBusConnection.DBusBusType busType = DaemonCommand.dBusType;
            ServiceEnvironment serviceEnvironment = c.getServiceEnvironment();
            TrustNewIdentity trustNewIdentity = DaemonCommand.trustNewIdentity;

            //create new manager for this number
            final Manager m = App.loadManager(number, settingsPath, serviceEnvironment, trustNewIdentity);
            addManager(m);
            final var thread = new Thread(() -> {
                try {
                    OutputWriter outputWriter = DaemonCommand.outputWriter;
                    boolean ignoreAttachments = false;
                    DBusConnection conn = DBusConnection.getConnection(busType);
                    while (!Thread.interrupted()) {
                        try {
                            final var receiveMessageHandler = outputWriter instanceof JsonWriter
                                    ? new JsonDbusReceiveMessageHandler(m, (JsonWriter) outputWriter, conn, objectPath)
                                    : new DbusReceiveMessageHandler(m, (PlainTextWriter) outputWriter, conn, objectPath);
                            m.receiveMessages(1, TimeUnit.HOURS, false, ignoreAttachments, receiveMessageHandler);
                            break;
                        } catch (IOException e) {
                            logger.warn("Receiving messages failed, retrying", e);
                        }
                    }
                } catch (DBusException e) {
                    throw new Error.Failure(e.getClass().getSimpleName() + " Listen error: " + e.getMessage());
                }
            });
        } catch (OverlappingFileLockException e) {
            logger.warn("Ignoring {}: {}", number, e.getMessage());
            throw new Error.Failure(e.getClass().getSimpleName() + " Already listening: " + e.getMessage());
        } catch (CommandException e) {
            logger.warn("Ignoring {}: {}", number, e.getMessage());
            throw new Error.Failure(e.getClass().getSimpleName() + " Listen error: " + e.getMessage());
        }
    }

    @Override
    public List<DBusPath> listAccounts() {
        synchronized (receiveThreads) {
            return receiveThreads.stream()
                    .map(Pair::first)
                    .map(Manager::getSelfNumber)
                    .map(u -> new DBusPath(DbusConfig.getObjectPath(u)))
                    .collect(Collectors.toList());
        }
    }
}
