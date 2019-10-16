package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;

public interface DbusCommand extends Command {

    int handleCommand(Namespace ns, Signal signal);
}
