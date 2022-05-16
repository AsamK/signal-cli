package org.asamk.signal.dbus;

import org.asamk.SignalControl;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.PinLockedException;
import org.freedesktop.dbus.connections.impl.DBusConnection;

import java.io.IOException;

/**
 * This class implements the RegistrationManager interface using the DBus Signal interface, where possible.
 * It's used for the signal-cli dbus client mode (--dbus, --dbus-system)
 */
public class DbusRegistrationManagerImpl implements RegistrationManager {

    private final String number;
    private final SignalControl signalControl;
    private final DBusConnection connection;

    public DbusRegistrationManagerImpl(String number, final SignalControl signalControl, DBusConnection connection) {
        this.number = number;
        this.signalControl = signalControl;
        this.connection = connection;
    }

    @Override
    public void register(
            final boolean voiceVerification, final String captcha
    ) throws IOException, CaptchaRequiredException {
        if (captcha == null) {
            signalControl.register(number, voiceVerification);
        } else {
            signalControl.registerWithCaptcha(number, voiceVerification, captcha);
        }
    }

    @Override
    public void verifyAccount(
            final String verificationCode, final String pin
    ) throws IOException, PinLockedException, IncorrectPinException {
        if (pin == null) {
            signalControl.verify(number, verificationCode);
        } else {
            signalControl.verifyWithPin(number, verificationCode, pin);
        }
    }

    @Override
    public void deleteLocalAccountData() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRegistered() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
    }
}
