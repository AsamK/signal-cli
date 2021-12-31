package org.asamk;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;

import java.util.List;

/**
 * DBus interface for the org.asamk.SignalControl interface.
 * Including emitted Signals and returned Errors.
 */
public interface SignalControl extends DBusInterface {

    void register(
            String number, boolean voiceVerification
    ) throws Error.Failure, Error.InvalidNumber, Error.RequiresCaptcha;

    void registerWithCaptcha(
            String number, boolean voiceVerification, String captcha
    ) throws Error.Failure, Error.InvalidNumber, Error.RequiresCaptcha;

    void verify(String number, String verificationCode) throws Error.Failure, Error.InvalidNumber;

    void verifyWithPin(String number, String verificationCode, String pin) throws Error.Failure, Error.InvalidNumber;

    String link(String newDeviceName) throws Error.Failure;

    String startLink() throws Error.Failure;

    String finishLink(String deviceLinkUri, String newDeviceName) throws Error.Failure;

    String version();

    List<DBusPath> listAccounts();

    DBusPath getAccount(String number);

    interface Error {

        class Failure extends DBusExecutionException {

            public Failure(final String message) {
                super(message);
            }
        }

        class InvalidNumber extends DBusExecutionException {

            public InvalidNumber(final String message) {
                super(message);
            }
        }

        class RequiresCaptcha extends DBusExecutionException {

            public RequiresCaptcha(final String message) {
                super(message);
            }
        }
    }
}
