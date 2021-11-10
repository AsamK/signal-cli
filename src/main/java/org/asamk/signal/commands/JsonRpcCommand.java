package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import org.asamk.signal.OutputType;

import java.util.List;

public interface JsonRpcCommand<T> extends Command {

    default TypeReference<T> getRequestType() {
        return null;
    }

    default List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.JSON);
    }
}
