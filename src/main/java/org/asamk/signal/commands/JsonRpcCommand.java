package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import org.asamk.signal.OutputType;

import java.util.List;
import java.util.SequencedCollection;

public interface JsonRpcCommand<T> extends Command {

    default TypeReference<T> getRequestType() {
        return null;
    }

    default SequencedCollection<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.JSON);
    }
}
