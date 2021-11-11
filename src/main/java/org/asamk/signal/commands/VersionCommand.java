package org.asamk.signal.commands;

import org.asamk.signal.BaseConfig;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;

import java.util.Map;

public class VersionCommand implements JsonRpcSingleCommand<Void>, JsonRpcMultiCommand<Void> {

    @Override
    public String getName() {
        return "version";
    }

    @Override
    public void handleCommand(
            final Void request, final Manager m, final JsonWriter jsonWriter
    ) throws CommandException {
        outputVersion(jsonWriter);
    }

    @Override
    public void handleCommand(
            final Void request, final MultiAccountManager c, final JsonWriter jsonWriter
    ) throws CommandException {
        outputVersion(jsonWriter);
    }

    private void outputVersion(final JsonWriter jsonWriter) {
        jsonWriter.write(Map.of("version", BaseConfig.PROJECT_VERSION));
    }
}
