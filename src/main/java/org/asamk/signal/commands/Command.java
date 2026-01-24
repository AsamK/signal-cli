package org.asamk.signal.commands;

import org.asamk.signal.OutputType;

import java.util.List;
import java.util.SequencedCollection;

public interface Command {

    String getName();

    default SequencedCollection<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT);
    }
}
