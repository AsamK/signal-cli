package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Subparser;

public interface SubparserAttacher {

    void attachToSubparser(final Subparser subparser);
}
