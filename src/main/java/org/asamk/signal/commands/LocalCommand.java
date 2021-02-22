package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;

public interface LocalCommand extends Command {

    void handleCommand(Namespace ns, Manager m) throws CommandException;
}
