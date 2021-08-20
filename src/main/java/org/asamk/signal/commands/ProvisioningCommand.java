package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.ProvisioningManager;

public interface ProvisioningCommand extends CliCommand {

    void handleCommand(Namespace ns, ProvisioningManager m, final OutputWriter outputWriter) throws CommandException;
}
