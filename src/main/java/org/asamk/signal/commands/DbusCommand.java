package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;

public interface DbusCommand extends LocalCommand {

    void handleCommand(Namespace ns, Signal signal, OutputWriter outputWriter) throws CommandException;

    default void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        handleCommand(ns, new DbusSignalImpl(m, null), outputWriter);
    }
}
