package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.BaseConfig;
import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

import java.util.List;
import java.util.Map;

public class VersionCommand implements JsonRpcLocalCommand, JsonRpcMultiLocalCommand {

    @Override
    public String getName() {
        return "version";
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        outputVersion(outputWriter);
    }

    @Override
    public void handleCommand(
            final Namespace ns, final MultiAccountManager c, final OutputWriter outputWriter
    ) throws CommandException {
        outputVersion(outputWriter);
    }

    @Override
    public TypeReference<Map<String, Object>> getRequestType() {
        return new TypeReference<>() {};
    }

    private void outputVersion(final OutputWriter outputWriter) {
        final var projectName = BaseConfig.PROJECT_NAME == null ? "signal-cli" : BaseConfig.PROJECT_NAME;
        final var version = BaseConfig.PROJECT_VERSION == null ? "unknown" : BaseConfig.PROJECT_VERSION;

        switch (outputWriter) {
            case JsonWriter jsonWriter -> jsonWriter.write(Map.of("version", version));
            case PlainTextWriter plainTextWriter -> plainTextWriter.println("{} {}", projectName, version);
        }
    }
}
