package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import org.asamk.signal.OutputType;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;

import java.util.List;

public interface JsonRpcCommand<T> extends Command {

    default TypeReference<T> getRequestType() {
        return null;
    }

    void handleCommand(T request, Manager m, OutputWriter outputWriter) throws CommandException;

    default List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.JSON);
    }
}
