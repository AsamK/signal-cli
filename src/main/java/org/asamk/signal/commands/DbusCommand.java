package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;

public interface DbusCommand extends LocalCommand {

    void handleCommand(Namespace ns, Signal signal) throws CommandException;

    default void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        handleCommand(ns, new DbusSignalImpl(m));
    }
}
