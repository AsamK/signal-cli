package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Subparser;

public interface Command {

    void attachToSubparser(Subparser subparser);
}
