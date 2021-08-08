package org.asamk.signal.commands;

import org.asamk.signal.OutputType;

import java.util.Set;

public interface Command {

    default Set<OutputType> getSupportedOutputTypes() {
        return Set.of(OutputType.PLAIN_TEXT);
    }
}
