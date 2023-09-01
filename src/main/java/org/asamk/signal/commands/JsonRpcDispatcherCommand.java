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
        subparser.addArgument("--ignore-stories")
                .help("Don’t receive story messages from the server.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--send-read-receipts")
                .help("Send read receipts for all incoming data messages (in addition to the default delivery receipts)")
                .action(Arguments.storeTrue());
        subparser.addArgument("--receive-mode")
                .help("Specify when to start receiving messages.")
                .type(Arguments.enumStringType(ReceiveMode.class))
                .setDefault(ReceiveMode.ON_START);
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var receiveMode = ns.<ReceiveMode>get("receive-mode");
        final var receiveConfig = getReceiveConfig(ns);
        m.setReceiveConfig(receiveConfig);

        final var jsonOutputWriter = (JsonWriter) outputWriter;
        final var lineSupplier = getLineSupplier();

        final var handler = new SignalJsonRpcDispatcherHandler(jsonOutputWriter,
                lineSupplier,
                receiveMode == ReceiveMode.MANUAL);
        handler.handleConnection(m);
    }

    private static ReceiveConfig getReceiveConfig(final Namespace ns) {
        final var ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));
        final var ignoreStories = Boolean.TRUE.equals(ns.getBoolean("ignore-stories"));
        final var sendReadReceipts = Boolean.TRUE.equals(ns.getBoolean("send-read-receipts"));
        return new ReceiveConfig(ignoreAttachments, ignoreStories, sendReadReceipts);
    }

    private static Supplier<String> getLineSupplier() {
        return IOUtils.getLineSupplier(new InputStreamReader(System.in, IOUtils.getConsoleCharset()));
    }
}
