package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.freedesktop.dbus.connections.impl.DBusConnection;

public interface ExtendedDbusCommand extends Command {

    int handleCommand(Namespace ns, Signal signal, DBusConnection dbusconnection);
}
