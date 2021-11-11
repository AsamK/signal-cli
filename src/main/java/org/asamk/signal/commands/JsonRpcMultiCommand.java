package org.asamk.signal.commands;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.MultiAccountManager;

public interface JsonRpcMultiCommand<T> extends JsonRpcCommand<T> {

    void handleCommand(T request, MultiAccountManager c, JsonWriter jsonWriter) throws CommandException;
}
