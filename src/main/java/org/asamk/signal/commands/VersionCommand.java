package org.asamk.signal.commands;

import org.asamk.signal.BaseConfig;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;

import java.util.Map;

public class VersionCommand implements JsonRpcSingleCommand<Void> {

    @Override
    public String getName() {
        return "version";
    }

    @Override
    public void handleCommand(
            final Void request, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var jsonWriter = (JsonWriter) outputWriter;
        jsonWriter.write(Map.of("version", BaseConfig.PROJECT_VERSION));
    }
}
