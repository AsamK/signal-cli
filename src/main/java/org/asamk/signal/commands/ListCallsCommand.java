package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.CallInfo;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

import java.util.List;

public class ListCallsCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "listCalls";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("List active voice calls.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        var calls = m.listActiveCalls();
        switch (outputWriter) {
            case PlainTextWriter writer -> {
                if (calls.isEmpty()) {
                    writer.println("No active calls.");
                } else {
                    for (var call : calls) {
                        writer.println("- Call {}:", call.callId());
                        writer.indent(w -> {
                            w.println("State: {}", call.state());
                            w.println("Recipient: {}", call.recipient());
                            w.println("Direction: {}", call.isOutgoing() ? "outgoing" : "incoming");
                            if (call.inputDeviceName() != null) {
                                w.println("Input device: {}", call.inputDeviceName());
                            }
                            if (call.outputDeviceName() != null) {
                                w.println("Output device: {}", call.outputDeviceName());
                            }
                        });
                    }
                }
            }
            case JsonWriter writer -> {
                var jsonCalls = calls.stream()
                        .map(c -> new JsonCall(c.callId(),
                                c.state().name(),
                                c.recipient().number().orElse(null),
                                c.recipient().uuid().map(java.util.UUID::toString).orElse(null),
                                c.isOutgoing(),
                                c.inputDeviceName(),
                                c.outputDeviceName()))
                        .toList();
                writer.write(jsonCalls);
            }
        }
    }

    private record JsonCall(
            long callId,
            String state,
            String number,
            String uuid,
            boolean isOutgoing,
            String inputDeviceName,
            String outputDeviceName
    ) {}
}
