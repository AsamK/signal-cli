package org.asamk.signal.commands;

import org.asamk.signal.OutputType;

import java.util.List;

public interface Command {

    String getName();

    default List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT);
    }
}
