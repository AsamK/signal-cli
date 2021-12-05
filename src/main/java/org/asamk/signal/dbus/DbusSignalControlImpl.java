package org.asamk.signal.dbus;

import org.asamk.SignalControl;
import org.asamk.signal.BaseConfig;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.UserAlreadyExists;
import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.PinLockedException;
import org.freedesktop.dbus.DBusPath;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.OverlappingFileLockException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class DbusSignalControlImpl implements org.asamk.SignalControl {

    private final MultiAccountManager c;

    private final String objectPath;

    public DbusSignalControlImpl(final MultiAccountManager c, final String objectPath) {
        this.c = c;
        this.objectPath = objectPath;
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
        if (!Manager.isValidNumber(number, null)) {
            throw new SignalControl.Error.InvalidNumber(
                    "Invalid account (phone number), make sure you include the country code.");
        }
        try (final RegistrationManager registrationManager = c.getNewRegistrationManager(number)) {
            registrationManager.register(voiceVerification, captcha);
        } catch (CaptchaRequiredException e) {
            String message = captcha == null ? "Captcha required for verification." : "Invalid captcha given.";
            throw new SignalControl.Error.RequiresCaptcha(message);
        } catch (OverlappingFileLockException e) {
            throw new SignalControl.Error.Failure("Account is already in use");
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
            registrationManager.verifyAccount(verificationCode, pin);
        } catch (OverlappingFileLockException e) {
            throw new SignalControl.Error.Failure("Account is already in use");
        } catch (IOException | PinLockedException | IncorrectPinException e) {
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
                    provisioningManager.finishDeviceLink(newDeviceName);
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
        return c.getAccountNumbers()
                .stream()
                .map(u -> new DBusPath(DbusConfig.getObjectPath(u)))
                .collect(Collectors.toList());
    }
}
