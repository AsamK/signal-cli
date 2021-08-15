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

	List<DBusPath> listAccounts();

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
