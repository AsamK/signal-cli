package org.asamk.signal.commands;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.commands.exceptions.CommandException;

public interface JsonRpcMultiCommand<T> extends JsonRpcCommand<T> {

    void handleCommand(T request, SignalCreator c, JsonWriter jsonWriter) throws CommandException;
}
