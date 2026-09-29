package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SequencedCollection;

public final class ExportHistoryCommand implements LocalCommand, JsonRpcSingleCommand<ExportHistoryCommand.ExportHistoryParams> {

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
    public TypeReference<ExportHistoryParams> getRequestType() {
        return new TypeReference<>() {};
    }

    @Override
    public void handleCommand(
            final ExportHistoryParams request,
            final Manager manager,
            final JsonWriter jsonWriter
    ) throws CommandException {
        final var offset = request == null || request.offset() == null ? 0 : request.offset();
        final var limit = request == null || request.limit() == null ? 100 : request.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new UserErrorException(
                    "History offset must be non-negative and limit must be between 1 and 100");
        }
        final var messages = new ArrayList<Object>();
        final var handler = new JsonReceiveMessageHandler(manager, messages::add);
        final var seen = new long[] {0};
        final var pageHandler = (Manager.ReceiveMessageHandler) (envelope, exception) -> {
            if (seen[0]++ >= offset && messages.size() < limit) {
                handler.handleMessage(envelope, exception);
            }
        };
        try {
            final var result = manager.exportHistory(pageHandler, false);
            jsonWriter.write(new JsonHistoryPage(messages,
                    result.exportedMessages(),
                    result.skippedRecords(),
                    (long) offset + messages.size() < result.exportedMessages()));
        } catch (IOException e) {
            throw new IOErrorException("Failed to export linked-device history: " + e.getMessage(), e);
        }
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

    public record ExportHistoryParams(Integer offset, Integer limit) {}

    private record JsonHistoryPage(List<Object> messages, int exportedMessages, int skippedRecords, boolean hasMore) {}
}
