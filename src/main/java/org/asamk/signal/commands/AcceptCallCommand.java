package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

import java.io.IOException;

public class AcceptCallCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "acceptCall";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Accept an incoming voice call.");
        subparser.addArgument("--call-id")
                .type(long.class)
                .required(true)
                .help("The call ID to accept.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var callIdNumber = ns.get("call-id");
        if (callIdNumber == null) {
            throw new UserErrorException("No call ID given");
        }
        final long callId = ((Number) callIdNumber).longValue();

        try {
            var callInfo = m.acceptCall(callId);
            switch (outputWriter) {
                case PlainTextWriter writer -> {
                    writer.println("Call accepted:");
                    writer.println("  Call ID: {}", callInfo.callId());
                    writer.println("  State: {}", callInfo.state());
                    writer.println("  Input device: {}", callInfo.inputDeviceName());
                    writer.println("  Output device: {}", callInfo.outputDeviceName());
                }
                case JsonWriter writer -> writer.write(new JsonCallInfo(callInfo.callId(),
                        callInfo.state().name(),
                        callInfo.inputDeviceName(),
                        callInfo.outputDeviceName(),
                        "opus",
                        48000,
                        1,
                        20));
            }
        } catch (IOException e) {
            throw new IOErrorException("Failed to accept call: " + e.getMessage(), e);
        }
    }

    private record JsonCallInfo(
            long callId,
            String state,
            String inputDeviceName,
            String outputDeviceName,
            String codec,
            int sampleRate,
            int channels,
            int ptimeMs
    ) {}
}
