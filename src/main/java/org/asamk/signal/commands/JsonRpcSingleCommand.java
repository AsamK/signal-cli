package org.asamk.signal.commands;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.JsonWriter;

public interface JsonRpcSingleCommand<T> extends JsonRpcCommand<T> {

    void handleCommand(T request, Manager m, JsonWriter jsonWriter) throws CommandException;
}
