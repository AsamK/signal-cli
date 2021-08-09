package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;

import java.util.Set;

public interface JsonRpcCommand<T> extends Command {

    default TypeReference<T> getRequestType() {
        return null;
    }

    void handleCommand(T request, Manager m) throws CommandException;

    default Set<OutputType> getSupportedOutputTypes() {
        return Set.of(OutputType.JSON);
    }
}
