package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.freedesktop.dbus.connections.impl.DBusConnection;

public interface ExtendedDbusCommand extends CliCommand {

    void handleCommand(
            Namespace ns, Signal signal, DBusConnection dbusconnection, final OutputWriter outputWriter
    ) throws CommandException;
}
