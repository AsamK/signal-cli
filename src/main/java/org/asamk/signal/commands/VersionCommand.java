package org.asamk.signal.commands;

import org.asamk.signal.BaseConfig;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;

import java.util.Map;

public class VersionCommand implements JsonRpcCommand<Void> {

    private final OutputWriter outputWriter;

    public VersionCommand(final OutputWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    @Override
    public void handleCommand(final Void request, final Manager m) throws CommandException {
        final var jsonWriter = (JsonWriter) outputWriter;
        jsonWriter.write(Map.of("version", BaseConfig.PROJECT_VERSION));
    }
}
