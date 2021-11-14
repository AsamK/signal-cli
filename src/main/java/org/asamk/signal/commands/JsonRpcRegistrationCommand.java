package org.asamk.signal.commands;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.output.JsonWriter;

public interface JsonRpcRegistrationCommand<T> extends JsonRpcCommand<T> {

    void handleCommand(T request, RegistrationManager m, JsonWriter jsonWriter) throws CommandException;
}
