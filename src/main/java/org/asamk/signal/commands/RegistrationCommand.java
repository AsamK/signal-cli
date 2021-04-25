package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.RegistrationManager;

public interface RegistrationCommand extends Command {

    void handleCommand(Namespace ns, RegistrationManager m) throws CommandException;
}
