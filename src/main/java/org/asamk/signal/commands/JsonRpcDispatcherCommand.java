package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.jsonrpc.SignalJsonRpcDispatcherHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.ReceiveConfig;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.List;
import java.util.function.Supplier;

public class JsonRpcDispatcherCommand implements LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(JsonRpcDispatcherCommand.class);

    @Override
    public String getName() {
        return "jsonRpc";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Take commands from standard input as line-delimited JSON RPC while receiving messages.");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final boolean ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));
        m.setReceiveConfig(new ReceiveConfig(ignoreAttachments));

        final var jsonOutputWriter = (JsonWriter) outputWriter;
        final Supplier<String> lineSupplier = IOUtils.getLineSupplier(new InputStreamReader(System.in,
                IOUtils.getConsoleCharset()));

        final var handler = new SignalJsonRpcDispatcherHandler(jsonOutputWriter, lineSupplier, false);
        handler.handleConnection(m);
    }
}
