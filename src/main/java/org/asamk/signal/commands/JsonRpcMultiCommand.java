package org.asamk.signal.commands;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.JsonWriter;

public interface JsonRpcMultiCommand<T> extends JsonRpcCommand<T> {

    void handleCommand(T request, MultiAccountManager c, JsonWriter jsonWriter) throws CommandException;
}
