package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.ProvisioningManager;

public interface ProvisioningCommand extends Command {

    void handleCommand(Namespace ns, ProvisioningManager m) throws CommandException;
}
