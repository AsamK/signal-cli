package org.asamk.signal.dbus;

import org.asamk.SignalControl;
import org.asamk.signal.BaseConfig;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.commands.SignalCreator;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.UserAlreadyExists;
import org.asamk.signal.manager.api.Pair;
import org.freedesktop.dbus.DBusPath;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DbusSignalControlImpl implements org.asamk.SignalControl {

    private final SignalCreator c;
    private final Function<Manager, Thread> newManagerRunner;

    private final List<Pair<Manager, Thread>> receiveThreads = new ArrayList<>();
    private final Object stopTrigger = new Object();
    private final String objectPath;

    public DbusSignalControlImpl(
            final SignalCreator c, final Function<Manager, Thread> newManagerRunner, final String objectPath
    ) {
        this.c = c;
        this.newManagerRunner = newManagerRunner;
        this.objectPath = objectPath;
    }

    public void addManager(Manager m) {
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
            addManager(manager);
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
                    addManager(manager);
                } catch (IOException | TimeoutException | UserAlreadyExists e) {
                    e.printStackTrace();
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
