package org.asamk.signal;

import org.asamk.signal.manager.Manager;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public class DbusReceiveMessageHandler extends ReceiveMessageHandler {

    private final DBusConnection conn;
    private final String objectPath;

    public DbusReceiveMessageHandler(Manager m, DBusConnection conn, final String objectPath) {
        super(m);
        this.conn = conn;
        this.objectPath = objectPath;
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        super.handleMessage(envelope, content, exception);

        JsonDbusReceiveMessageHandler.sendReceivedMessageToDbus(envelope, content, conn, objectPath, m);
    }
}
