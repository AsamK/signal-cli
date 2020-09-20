package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.manager.ProvisioningManager;

public interface ProvisioningCommand extends Command {

    int handleCommand(Namespace ns, ProvisioningManager m);
}
