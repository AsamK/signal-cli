package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;

import java.util.List;

public interface MultiLocalCommand extends LocalCommand {

    void handleCommand(Namespace ns, List<Manager> m) throws CommandException;

    @Override
    default void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        handleCommand(ns, List.of(m));
    }
}
