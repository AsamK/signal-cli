package org.asamk.signal.dbus;

import org.asamk.SignalControl;
import org.asamk.signal.App;
import org.asamk.signal.BaseConfig;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.commands.SignalCreator;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.PathConfig;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.UserAlreadyExists;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.freedesktop.dbus.DBusPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DbusSignalControlImpl implements org.asamk.SignalControl {

    private static SignalCreator c;
    private static Function<Manager, Thread> newManagerRunner;

    private static List<Pair<Manager, Thread>> receiveThreads = new ArrayList<>();
    private static Object stopTrigger = new Object();
    private static String objectPath;

    public static RegistrationManager registrationManager;
    public static ProvisioningManager provisioningManager;

    private final static Logger logger = LoggerFactory.getLogger(DbusSignalControlImpl.class);

    public DbusSignalControlImpl(
            final SignalCreator c, final Function<Manager, Thread> newManagerRunner, final String objectPath
    ) {
        this.c = c;
        this.newManagerRunner = newManagerRunner;
        this.objectPath = objectPath;
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

/*
    @Override
    public boolean isRemote() {
        return false;
    }
*/

    @Override
    public String getObjectPath() {
        return objectPath;
    }

    public static void register(
            final String number, final boolean voiceVerification
    ) {
        registerWithCaptcha(number, voiceVerification, null);
    }

    public static void registerWithCaptcha(
            final String number, final boolean voiceVerification, final String captcha
    ) {
        try  {
            try {
                registrationManager =  RegistrationManager.init(number, App.dataPath, App.serviceEnvironment, BaseConfig.USER_AGENT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            registrationManager.register(voiceVerification, captcha);
        } catch (CaptchaRequiredException e) {
            try {
                registrationManager.close();
            } catch (IOException f) {
                throw new SignalControl.Error.Failure(f.getClass().getSimpleName() + " " + f.getMessage());
            }
            String message = captcha == null ? "Captcha required for verification. Get one from https://signalcaptchas.org/registration/generate.html"
                            : "Invalid captcha given. Get one from https://signalcaptchas.org/registration/generate.html";
            throw new SignalControl.Error.RequiresCaptcha(message);
        } catch (IOException e) {
            throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    public static void verify(final String number, final String verificationCode) {
        verifyWithPin(number, verificationCode, null);
    }

    public static void verifyWithPin(final String number, final String verificationCode, final String pin)
    {
        try {
            final Manager manager = registrationManager.verifyAccount(verificationCode, pin);
            logger.info("Registration of " + number + " verified");
            manager.close();
            registrationManager.close();
        } catch (IOException | KeyBackupSystemNoDataException | KeyBackupServicePinException e) {
            throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    public static String link() {
        return link("cli");
    }

    public static String link(final String newDeviceName) {
        try {
            provisioningManager = ProvisioningManager.init(App.dataPath, App.serviceEnvironment, BaseConfig.USER_AGENT);
            final URI deviceLinkUri = provisioningManager.getDeviceLinkUri();
            new Thread(() -> {
                try {
                    Manager manager = provisioningManager.finishDeviceLink(newDeviceName);
                    logger.info("Linking of " + newDeviceName + " successful");
                    manager.close();
                } catch (TimeoutException e) {
                    throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + "Link request timed out, please try again.");
                } catch (IOException e) {
                    throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + "Link request error: " + e.getMessage());
                } catch (UserAlreadyExists e) {
                    throw new SignalControl.Error.Failure(e.getClass().getSimpleName() + "The user "
                            + e.getUsername()
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

    public static String version() {
        return BaseConfig.PROJECT_VERSION;
    }

    @Override
    public List<DBusPath> listAccounts() {
        synchronized (receiveThreads) {
            return receiveThreads.stream()
                    .map(Pair::first)
                    .map(Manager::getUsername)
                    .map(u -> new DBusPath(DbusConfig.getObjectPath(u)))
                    .collect(Collectors.toList());
        }
    }
}
