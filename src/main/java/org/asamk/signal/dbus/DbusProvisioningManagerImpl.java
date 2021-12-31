package org.asamk.signal.dbus;

import org.asamk.SignalControl;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.UserAlreadyExists;
import org.freedesktop.dbus.connections.impl.DBusConnection;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

/**
 * This class implements the ProvisioningManager interface using the DBus Signal interface, where possible.
 * It's used for the signal-cli dbus client mode (--dbus, --dbus-system)
 */
public class DbusProvisioningManagerImpl implements ProvisioningManager {

    private final SignalControl signalControl;
    private final DBusConnection connection;

    private URI deviceLinkUri;

    public DbusProvisioningManagerImpl(final SignalControl signalControl, DBusConnection connection) {
        this.signalControl = signalControl;
        this.connection = connection;
    }

    public DbusProvisioningManagerImpl(
            final SignalControl signalControl,
            DBusConnection connection,
            URI deviceLinkUri
    ) {
        this.signalControl = signalControl;
        this.connection = connection;
        this.deviceLinkUri = deviceLinkUri;
    }

    @Override
    public URI getDeviceLinkUri() throws TimeoutException, IOException {
        try {
            deviceLinkUri = new URI(signalControl.startLink());
            return deviceLinkUri;
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    @Override
    public String finishDeviceLink(final String deviceName) throws IOException, TimeoutException, UserAlreadyExists {
        return signalControl.finishLink(deviceLinkUri.toString(), deviceName);
    }
}
