package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import org.asamk.signal.manager.Manager;

public interface LocalCommand extends Command {

    int handleCommand(Namespace ns, Manager m);
}
