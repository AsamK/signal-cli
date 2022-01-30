package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.SignalControl;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * This class implements the MultiAccountManager interface using the DBus Signal interface, where possible.
 * It's used for the signal-cli dbus client mode (--dbus, --dbus-system)
 */
public class DbusMultiAccountManagerImpl implements MultiAccountManager {

    private final SignalControl signalControl;
    private final DBusConnection connection;
    // TODO add listeners for added/removed accounts
    private final Set<Consumer<Manager>> onManagerAddedHandlers = new HashSet<>();
    private final Set<Consumer<Manager>> onManagerRemovedHandlers = new HashSet<>();

    public DbusMultiAccountManagerImpl(final SignalControl signalControl, DBusConnection connection) {
        this.signalControl = signalControl;
        this.connection = connection;
    }

    @Override
    public List<String> getAccountNumbers() {
        return signalControl.listAccounts()
                .stream()
                .map(a -> getRemoteObject(a, Signal.class).getSelfNumber())
                .toList();
    }

    @Override
    public List<Manager> getManagers() {
        return signalControl.listAccounts()
                .stream()
                .map(a -> (Manager) new DbusManagerImpl(getRemoteObject(a, Signal.class), connection))
                .toList();
    }

    @Override
    public void addOnManagerAddedHandler(final Consumer<Manager> handler) {
        synchronized (onManagerAddedHandlers) {
            onManagerAddedHandlers.add(handler);
        }
    }

    @Override
    public void addOnManagerRemovedHandler(final Consumer<Manager> handler) {
        synchronized (onManagerRemovedHandlers) {
            onManagerRemovedHandlers.add(handler);
        }
    }

    @Override
    public Manager getManager(final String phoneNumber) {
        return new DbusManagerImpl(getRemoteObject(signalControl.getAccount(phoneNumber), Signal.class), connection);
    }

    @Override
    public URI getNewProvisioningDeviceLinkUri() throws TimeoutException, IOException {
        try {
            return new URI(signalControl.startLink());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    @Override
    public ProvisioningManager getProvisioningManagerFor(final URI deviceLinkUri) {
        return new DbusProvisioningManagerImpl(signalControl, connection, deviceLinkUri);
    }

    @Override
    public RegistrationManager getNewRegistrationManager(final String account) throws IOException {
        return new DbusRegistrationManagerImpl(account, signalControl, connection);
    }

    @Override
    public void close() {
    }

    private <T extends DBusInterface> T getRemoteObject(final DBusPath path, final Class<T> type) {
        try {
            return connection.getRemoteObject(DbusConfig.getBusname(), path.getPath(), type);
        } catch (DBusException e) {
            throw new AssertionError(e);
        }
    }
}
