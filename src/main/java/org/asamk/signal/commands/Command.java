package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;

import java.util.Set;

public interface Command {

    void attachToSubparser(Subparser subparser);

    default Set<OutputType> getSupportedOutputTypes() {
        return Set.of(OutputType.PLAIN_TEXT);
    }
}
