package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.output.OutputWriter;

public interface ProvisioningCommand extends CliCommand {

    void handleCommand(Namespace ns, ProvisioningManager m, final OutputWriter outputWriter) throws CommandException;
}
