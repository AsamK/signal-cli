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

public class RejectCallCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "rejectCall";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Reject an incoming voice call.");
        subparser.addArgument("--call-id")
                .type(long.class)
                .required(true)
                .help("The call ID to reject.");
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
            m.rejectCall(callId);
            switch (outputWriter) {
                case PlainTextWriter writer -> writer.println("Call {} rejected.", callId);
                case JsonWriter writer -> writer.write(new JsonResult(callId, "rejected"));
            }
        } catch (IOException e) {
            throw new IOErrorException("Failed to reject call: " + e.getMessage(), e);
        }
    }

    private record JsonResult(long callId, String status) {}
}
