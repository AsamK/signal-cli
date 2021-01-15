package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;

public interface DbusCommand extends LocalCommand {

    int handleCommand(Namespace ns, Signal signal);

    default int handleCommand(final Namespace ns, final Manager m) {
        return handleCommand(ns, new DbusSignalImpl(m));
    }
}
