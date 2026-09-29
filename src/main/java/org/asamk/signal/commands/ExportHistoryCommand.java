package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.SequencedCollection;

public final class ExportHistoryCommand implements LocalCommand {

    private static final Logger logger = LoggerFactory.getLogger(ExportHistoryCommand.class);

    @Override
    public String getName() {
        return "export-history";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Export message history transferred while linking this device as JSON lines.");
        subparser.addArgument("--delete-after-export")
                .action(Arguments.storeTrue())
                .help("Delete the encrypted transfer archive and key after a successful export.");
    }

    @Override
    public SequencedCollection<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager manager,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var deleteAfterExport = Boolean.TRUE.equals(ns.getBoolean("delete-after-export"));
        final var handler = new JsonReceiveMessageHandler(manager, (JsonWriter) outputWriter);
        try {
            final var result = manager.exportHistory(handler, deleteAfterExport);
            logger.info("Exported {} history messages; skipped {} unsupported or filtered records",
                    result.exportedMessages(),
                    result.skippedRecords());
        } catch (IOException e) {
            throw new IOErrorException("Failed to export linked-device history: " + e.getMessage(), e);
        }
    }
}
