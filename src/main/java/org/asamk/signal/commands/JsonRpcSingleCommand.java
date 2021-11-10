package org.asamk.signal.commands;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;

public interface JsonRpcSingleCommand<T> extends JsonRpcCommand<T> {

    void handleCommand(T request, Manager m, JsonWriter jsonWriter) throws CommandException;
}
