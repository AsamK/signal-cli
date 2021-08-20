package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Subparser;

public interface CliCommand extends Command {

    void attachToSubparser(Subparser subparser);
}
