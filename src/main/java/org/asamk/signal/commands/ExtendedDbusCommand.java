package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.asamk.signal.commands.exceptions.CommandException;
import org.freedesktop.dbus.connections.impl.DBusConnection;

public interface ExtendedDbusCommand extends Command {

    void handleCommand(Namespace ns, Signal signal, DBusConnection dbusconnection) throws CommandException;
}
