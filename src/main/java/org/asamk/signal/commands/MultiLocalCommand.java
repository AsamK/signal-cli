package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.manager.Manager;

import java.util.List;

public interface MultiLocalCommand extends LocalCommand {

    int handleCommand(Namespace ns, List<Manager> m);

    @Override
    default int handleCommand(final Namespace ns, final Manager m) {
        return handleCommand(ns, List.of(m));
    }
}
